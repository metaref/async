(ns metaref.examples.tour
  (:require [metaref.async :as async :refer [<! >! chan go select! capacity]]
            :reload))

;; https://go.dev/tour/concurrency/1

(defn say [s]
  (dotimes [_ 5]
    (Thread/sleep 100))
  (prn s))

(defn example-1 []
  (go (say "world"))
  (say "hello"))

(comment
  (example-1)
  )

(defn sum [xs ch]
  (>! ch (reduce + xs)))

(defn example-2 []
  (let [xs   [7 2 8 -9 4 0]
        ch   (chan)
        half (/ (count xs) 2)
        _    (go (sum (subvec xs 0 half) ch))
        _    (go (sum (subvec xs half) ch))
        x    (<! ch)
        y    (<! ch)]
    (prn x y (+ x y))))

(comment

  (example-2)
  )

(defn example-3 []
  (let [ch (chan 10)] ;; changing this to 1 makes it deadlock
    (>! ch 1)
    (>! ch 2)
    (prn (<! ch))
    (prn (<! ch))))

(comment

  (example-3)
  )

(defn fib [n ch]
  (loop [x 0
         y 1
         n n]
    (if (zero? n)
      (async/close! ch)
      (do
        (>! ch x)
        (recur y (+ x y) (dec n))))))

(defn example-4 []
  (let [ch (chan 10)]
    (go (fib (capacity ch) ch))
    (loop [val (<! ch)]
      (when val
        (println val)
        (recur (<! ch))))))

;; Same as example-4 but using async/for-each
(defn example-4-2 []
  (let [ch (chan 10)]
    (go
      (fib (capacity ch) ch))
    (async/for-each [val ch]
      (println val))))

(comment

  (example-4)
  (example-4-2)
  )

(defn fib2 [ch <quit]
  (loop [x 0
         y 1]
    (let [<put     (go (>! ch x))
          [_ port] (select! [<put <quit])]
      (condp = port
        <put (recur y (+ x y))

        <quit (println "quit")))))

(defn example-5 []
  (let [ch (chan)
        <quit (chan)]
    (go (dotimes [_ 10]
          (println (<! ch)))
        (>! <quit 0))
    (fib2 ch <quit)))

(comment
  (example-5)
  )

(defn tick [ms]
  (let [ch (chan)]
    (go (while true
          (Thread/sleep ms)
          (>! ch :tick)))
    ch))

(defn after [ms]
  (let [ch (chan)]
    (go (Thread/sleep ms)
        (>! ch :after))
    ch))

(defn example-6 []
  (let [t    (tick 100)
        boom (after 500)]
    (loop []
      (let [[_ port] (select! [t boom] :default false)]
        (condp = port
          t (do (println "tick") (recur))

          boom (println "BOOM!")

          (do (println "    .")
              (Thread/sleep 50)
              (recur)))))))

(comment
  (example-6)
  )
