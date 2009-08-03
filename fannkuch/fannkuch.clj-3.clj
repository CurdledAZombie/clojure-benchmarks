;; Author: Andy Fingerhut (andy_fingerhut@alum.wustl.edu)
;; Date: July, 2009

;; This version is fairly slow.  Would be nice to speed it up without
;; getting too crazy in the implementation.

;;(set! *warn-on-reflection* true)

(ns clojure.benchmark.fannkuch
  (:use [clojure.contrib.combinatorics :only (lex-permutations)])
  )


(defn usage [exit-code]
  (println (format "usage: %s N" *file*))
  (println (format "    N must be a positive integer"))
  (. System (exit exit-code)))

(when (not= (count *command-line-args*) 1)
  (usage 1))
(when (not (re-matches #"^\d+$" (nth *command-line-args* 0)))
  (usage 1))
(def N (. Integer valueOf (nth *command-line-args* 0) 10))
(when (< N 1)
  (usage 1))


(defn left-rotate
  "Return a sequence that is the same as s, except that the first n >= 1 elements have been 'rotated left' by 1 position.

  Examples:
  user> (left-rotate '(1 2 3 4) 2)
  (2 1 3 4)
  user> (left-rotate '(1 2 3 4) 3)
  (2 3 1 4)
  user> (left-rotate '(1 2 3 4) 4)
  (2 3 4 1)
  user> (left-rotate '(1 2 3 4) 1)
  (1 2 3 4)"
  [s n]
  (concat (take (dec n) (rest s)) (list (first s)) (drop n s)))


(defn next-perm-in-fannkuch-order [n perm counts]
  (loop [perm perm
         counts counts
         i 1]
    (let [next-perm (left-rotate perm (inc i))
          dec-count (dec (counts i))
          next-i (inc i)]
      (if (zero? dec-count)
        (if (< next-i n)
          (recur next-perm (assoc counts i (inc i)) next-i)
          [nil nil])
        [next-perm (assoc counts i dec-count)]))))


(defn permutations-in-fannkuch-order-helper [n perm counts]
  (lazy-seq
    (let [[next-perm next-counts] (next-perm-in-fannkuch-order n perm counts)]
      (when next-perm
;        (println (str "::next-perm " (vec next-perm)
;                      " next-counts " next-counts "::"))
        (cons next-perm
              (permutations-in-fannkuch-order-helper n next-perm
                                                     next-counts))))))


(defn permutations-in-fannkuch-order [n]
  (lazy-seq
    (let [init-perm (vec (take n (iterate inc 1)))
          init-count init-perm]
      (cons init-perm
            (permutations-in-fannkuch-order-helper n init-perm init-count)))))


(defn fannkuch-of-permutation [perm]
  (loop [perm perm
	 flips (int 0)]
    (let [first-num (first perm)]
      (if (== 1 first-num)
	flips
	(let [flipped-perm (into (vec (reverse (subvec perm 0 first-num)))
				 (subvec perm first-num))]
	  (recur flipped-perm (inc flips)))))))


(defn fannkuch [N]
  (let [perms (lex-permutations (range 1 (inc N)))]
    (loop [s (seq perms)
	   maxflips (int 0)]
      (if s
	(let [perm (first s)]
	  (let [curflips (int (fannkuch-of-permutation perm))]
            (if (> curflips maxflips)
              (recur (seq (rest s)) curflips)
              (recur (seq (rest s)) maxflips))))
	;; else
	maxflips))))


;; This is quick compared to iterating through all permutations, so do
;; it separately.
(let [fannkuch-order-perms (permutations-in-fannkuch-order N)]
  (doseq [p (take 30 fannkuch-order-perms)]
    (println (apply str p))))

(println (format "Pfannkuchen(%d) = %d" N (fannkuch N)))

(. System (exit 0))