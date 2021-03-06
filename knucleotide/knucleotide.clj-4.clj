;; Author: Andy Fingerhut (andy_fingerhut@alum.wustl.edu)
;; Date: Jul 9, 2009

;; Clojure program intended for submission to the "Computer Language
;; Benchmarks Game" web site, for the k-nucleotide problem, described
;; briefly on the bottom of this web page.

;; http://shootout.alioth.debian.org/u32q/benchmark.php?test=knucleotide&lang=all

;; The benchmark is run on a 4-processor machine, and parallelism is
;; encouraged -- if it reduces the start to finish time of the
;; computation, you rank better on the "Elapsed secs" metric than
;; another program that takes longer start-to-finish, even if the
;; other program uses less total CPU computation time.  I will attempt
;; to add some parallelism later, by calculating the tally1, tally2,
;; tally3, ..., tally18 values in parallel before printing them in the
;; desired order (several other benchmark programs do this, e.g. the
;; C++ and C entries).

;; Until then, I'm looking for other suggestions for speeding up the
;; code, or reducing its memory usage.

(ns knucleotide
  (:gen-class))

(set! *warn-on-reflection* true)


(defn fasta-description-line
  "Return true when the line l is a FASTA description line"
  [l]
  (= \> (first (seq l))))


(defn fasta-description-line-beginning
  "Return true when the line l is a FASTA description line that begins with the string desc-str."
  [desc-str l]
  (and (fasta-description-line l)
       (= desc-str (subs l 1 (min (count l) (inc (count desc-str)))))))


(defn fasta-dna-str-with-desc-beginning
  "Take a sequence of lines from a FASTA format file, and a string desc-str.  Look for a FASTA record with a description that begins with desc-str, and if one is found, return its DNA sequence as a single (potentially quite long) string.  If input file is big, you'll save lots of memory if you call this function in a with-open for the file, and don't hold on to the head of the lines parameter."
  [desc-str lines]
  (when-let [x (drop-while (fn [l]
                             (not (fasta-description-line-beginning desc-str l)))
                           lines)]
    (when-let [x (seq x)]
      (let [y (take-while (fn [l] (not (fasta-description-line l)))
                          (map (fn [#^java.lang.String s] (.toUpperCase s))
                               (rest x)))]
        (apply str y)))))


;; Unfortunately, at least with (clojure-version)=1.1.0-alpha-SNAPSHOT
;; and java version 1.5.0_19 on an Intel Mac with OS X 10.5.7,
;; tally-keeps-head keeps a reference to the beginning of the sequence
;; 'things', and thus keeps memory for the entire sequence during the
;; computation, which is too much.

;; Why does it keep the head, when tally-loses-head does not?

;; (defn tally-keeps-head
;;   "Take a sequence things, and return a hash map h whose keys are the set of unique objects in the sequence, and where (h obj) is equal to the number of times obj occurs in the sequence."
;;   [things]
;;   (loop [h {}
;;          remaining things]
;;     (if-let [r (seq remaining)]
;;       (let [key (first r)]
;;         (recur (assoc h key (inc (get h key 0))) (rest r)))
;;       h)))


;; (defn tally-loses-head-helper
;;   "Like tally-keeps-head, except requires that you pass in an empty map as the first argument in order to return the same result.  However, it 'loses its head' (see 'Programming Clojure', pp. 139-140), thus using significantly less memory than tally-keeps-head if no one else is keeping a reference to the head of 'things'."
;;   [h things]
;;   (if-let [r (seq things)]
;;     (let [key (first r)]
;;       (recur (assoc h key (inc (get h key 0))) (rest r)))
;;     h))


;; (defn tally-loses-head
;;   "Same caller interface as tally-keeps-head, but it uses same memory as weird-tally-loses-head."
;;   [things]
;;   (tally-loses-head-helper {} things))


(defn dna-char-to-code-val
  [c]
  ({\A 0, \C 1, \T 2, \G 3} c))


(defn code-val-to-dna-char
  [val]
  ({0 \A, 1 \C, 2 \T, 3 \G} val))


(defn dna-str-to-key
  [s]
  (loop [key 0
	 offset (int 0)]
    (if (= offset (count s))
      key
      (let [c (nth s offset)
	    new-key (+ (bit-shift-left key 2) (dna-char-to-code-val c))]
	(recur new-key (inc offset))))))


(defn key-to-dna-str
  [k len]
  (apply str (map code-val-to-dna-char
		  (map (fn [pos] (bit-and 3 (bit-shift-right k pos)))
		       (range (* 2 (dec len)) -1 -2)))))


(defn tally-dna-subs-with-len
  [len dna-str]
  (let [left-shift-amount (int (* 2 (dec len)))]
    (loop [offset (int (- (count dna-str) len))
	   key (dna-str-to-key (subs dna-str offset (+ offset len)))
	   tally {key 1}]
      (if (zero? offset)
	tally
	(let [new-offset (int (dec offset))
	      new-first-char-code (dna-char-to-code-val
                                   (nth dna-str new-offset))
	      new-key (+ (bit-shift-right key 2)
			 (bit-shift-left new-first-char-code left-shift-amount))
	      new-tally (assoc tally new-key (inc (get tally new-key 0)))]
	  (recur new-offset new-key new-tally))))))


(defn all-tally-to-str
  [tally fn-key-to-str]
  (with-out-str
    (let [total (reduce + (vals tally))]
      (doseq [k (sort #(>= (tally %1) (tally %2))  ; sort by tally, largest first
                      (keys tally))]
        (printf "%s %.3f\n" (fn-key-to-str k)
                (double (* 100 (/ (tally k) total))))))))


(defn one-tally-to-str
  [dna-str tally]
  (format "%d\t%s" (get tally (dna-str-to-key dna-str) 0) dna-str))


(defn compute-one-part
  [dna-str part]
  (condp = part
    0 (all-tally-to-str (tally-dna-subs-with-len 1 dna-str)
			(fn [k] (key-to-dna-str k 1)))
    1 (all-tally-to-str (tally-dna-subs-with-len 2 dna-str)
			(fn [k] (key-to-dna-str k 2)))
    2 (one-tally-to-str "GGT"
			(tally-dna-subs-with-len 3 dna-str))
    3 (one-tally-to-str "GGTA"
			(tally-dna-subs-with-len 4 dna-str))
    4 (one-tally-to-str "GGTATT"
			(tally-dna-subs-with-len 6 dna-str))
    5 (one-tally-to-str "GGTATTTTAATT"
			(tally-dna-subs-with-len 12 dna-str))
    6 (one-tally-to-str "GGTATTTTAATTTATAGT"
			(tally-dna-subs-with-len 18 dna-str))))


(defn -main [& args]
  (with-open [br (java.io.BufferedReader. *in*)]
    (let [dna-str (fasta-dna-str-with-desc-beginning "THREE" (line-seq br))
          results (map #(compute-one-part dna-str %)
                       (range 7))]
      (doseq [r results]
        (println r)
        (flush)))))
