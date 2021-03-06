;; Author: Andy Fingerhut (andy_fingerhut@alum.wustl.edu)
;; Date: July, 2009

;; The function 'dot' is based on suggestions and improvements made by
;; these people posting to the Clojure Google group in April, 2009:

;; dmitri.sotnikov@gmail.com
;; William D. Lipe (atmcsld@gmail.com)
;; Paul Stadig (paul@stadig.name)
;; michael.messinides@invista.com
;; David Sletten


;; clj-run.sh sends the command line arguments, not including the
;; command name.

(ns mandelbrot
  (:gen-class)
  ;; Needed for BufferedOutputStream
  (:import (java.io BufferedOutputStream)))

(set! *warn-on-reflection* true)


(def max-iterations 50)
(def limit-square (double 4.0))

(defn dot [r i]
  (let [f2 (double 2.0)
        limit-square limit-square
        iterations-remaining max-iterations
        pr (double r)
        pi (double i)]
    ;; The loop below is similar to the one in the Perl subroutine dot
    ;; in mandelbrot.perl, with these name correspondences:
    ;; pr <-> Cr, pi <-> Ci, zi <-> Zi, zr <-> Zr, zr2 <-> Tr, zi2 <-> Ti
    (loop [zr (double 0.0)
           zi (double 0.0)
           zr2 (double 0.0)
           zi2 (double 0.0)
           iterations-remaining iterations-remaining]
      (if (and (not (neg? iterations-remaining))
               (< (+ zr2 zi2) limit-square))
        (let [new-zi (double (+ (* (* f2 zr) zi) pi))
              new-zr (double (+ (- zr2 zi2) pr))
              new-zr2 (double (* new-zr new-zr))
              new-zi2 (double (* new-zi new-zi))]
          (recur new-zr new-zi new-zr2 new-zi2 (dec iterations-remaining)))
        (neg? iterations-remaining)))))


(defn index-to-val [i scale-fac offset]
  (+ (* i scale-fac) offset))


(defn ubyte
  [val]
  (if (>= val 128)
    (byte (- val 256))
    (byte val)))


;; I had a much more sequence-y implementation of this before, but it
;; allocated garbage very quickly, which caused the program to slow
;; down dramatically once it hit the heap limit and start garbage
;; collecting frequently.

(defn compute-row
  [x-vals y]
  (loop [b (int 0)
	 num-filled-bits (int 0)
	 result []
	 x-vals x-vals]
    (if-let [s (seq x-vals)]
      ; then
      (let [new-bit (int (if (dot (first s) y) 1 0))
	    new-b (int (+ (bit-shift-left b 1) new-bit))]
	(if (= num-filled-bits 7)
	  (recur (int 0)
		 (int 0)
		 (conj result (ubyte new-b))
		 (rest s))
	  (recur new-b
		 (int (inc num-filled-bits))
		 result
		 (rest s))))
      ; else
      (if (= num-filled-bits 0)
	result
	(conj result (ubyte (bit-shift-left b (- 8 num-filled-bits))))))))


(defn my-lazy-map
  [f coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (cons (f (first s)) (my-lazy-map f (rest s))))))


(defn modified-pmap
  "Like pmap from Clojure 1.1, but with only as much parallelism as
  there are available processors.  Uses my-lazy-map instead of map
  from core.clj, since that version of map can use unwanted additional
  parallelism for chunked collections, like ranges."
  ([num-threads f coll]
     (if (== num-threads 1)
       (map f coll)
       (let [n (if (>= num-threads 2) (dec num-threads) 1)
             rets (my-lazy-map #(future (f %)) coll)
             step (fn step [[x & xs :as vs] fs]
                    (lazy-seq
                      (if-let [s (seq fs)]
                        (cons (deref x) (step xs (rest s)))
                        (map deref vs))))]
         (step rets (drop n rets)))))
  ([num-threads f coll & colls]
     (let [step (fn step [cs]
                  (lazy-seq
                    (let [ss (my-lazy-map seq cs)]
                      (when (every? identity ss)
                        (cons (my-lazy-map first ss) (step (my-lazy-map rest ss)))))))]
       (modified-pmap num-threads #(apply f %) (step (cons coll colls))))))
  

;;(defn noisy-compute-row [x-vals y-val-ind two-over-size y-offset]
;;  (println (str "noisy-compute-row begin " y-val-ind))
;;  (let [ret-val (compute-row x-vals
;;                             (index-to-val y-val-ind two-over-size y-offset))]
;;    (println (str "noisy-compute-row end " y-val-ind))
;;    ret-val))


(defn compute-rows [size num-threads]
  (let [two-over-size (double (/ 2.0 size))
        x-offset (double -1.5)
        y-offset (double -1.0)
        x-vals (map #(index-to-val % two-over-size x-offset) (range size))]
    (modified-pmap num-threads
                   #(compute-row x-vals
                                 (index-to-val % two-over-size y-offset))
;;                   #(noisy-compute-row x-vals % two-over-size y-offset)
                   (range size))))


(defn do-mandelbrot [size num-threads print-in-text-format]
  (let [rows (compute-rows size num-threads)]
    (printf "P4\n")
    (printf "%d %d\n" size size)
    (flush)
    (if print-in-text-format
      (doseq [r rows]
        (doseq [byte r]
          (printf " %02x" byte))
        (newline))
      ;; else print in default PBM format
      (let [ostream (BufferedOutputStream. System/out)]
        (doseq [r rows]
          (. ostream write (into-array Byte/TYPE r) 0 (count r)))
        (. ostream close)))
    (flush)))


(def *default-modified-pmap-num-threads*
     (+ 2 (.. Runtime getRuntime availableProcessors)))

(defn usage [exit-code]
  (printf "usage: %s size [num-threads [print-in-text-format]]\n"
          *file*)
  (printf "    size must be a positive integer\n")
  (printf "    num-threads is the maximum threads to use at once\n")
  (printf "        during the computation.  If 0 or not given, it\n")
  (printf "        defaults to the number of available cores plus 2,\n")
  (printf "        which is %d\n"
          *default-modified-pmap-num-threads*)
  (flush)
  (. System (exit exit-code)))


(defn -main [& args]
  (when (or (< (count args) 1) (> (count args) 3))
    (usage 1))
  (when (not (re-matches #"^\d+$" (nth args 0)))
    (usage 1))
  (def size (. Integer valueOf (nth args 0) 10))
  (when (< size 1)
    (usage 1))
  (def num-threads
       (if (>= (count args) 2)
         (do
           (when (not (re-matches #"^\d+$" (nth args 1)))
             (usage 1))
           (let [n (. Integer valueOf (nth args 1) 10)]
             (if (== n 0)
               *default-modified-pmap-num-threads*
               n)))
         *default-modified-pmap-num-threads*))
  (def print-in-text-format (= (count args) 3))
  
  (do-mandelbrot size num-threads print-in-text-format)
  (shutdown-agents))
