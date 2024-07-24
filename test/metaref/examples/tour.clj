(ns metaref.examples.tour
  (:require [metaref.async :as async :refer [<! >! chan go select!]]
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

(defmacro with-std-out [& body]
  `(locking System/out
     ~@body))

(defn safe-println [& more]
  (.write *out* (str (clojure.string/join " " more) "\n")))

(defn fib [n ch]
  (loop [x 0
         y 1
         n n]
    #_(with-std-out
        (println "fib n " n))
    (if (zero? n)
      (async/close! ch)
      (do
        (.put ch x)
        (recur y (+ x y) (dec n))))))

(defn example-4 []
  (let [ch (chan 3)]
    (go (fib 3 ch))
    (loop [val (<! ch)]
      (when val
        (safe-println "ex 4 val " val)
        (recur (<! ch))))
    (safe-println "done")))

(defn example-4-2 []
  (let [r  (atom [])
        ch (chan 10)]
    (go
      (fib 10 ch))
    (async/for-each [val ch]
                      (swap! r conj val))
    #_(loop [val (.take ch)]
      (when val
        (swap! r conj val)
        (recur (.take ch))))
    @r))

(comment

  (example-4)

  (try
    (let [*failed (atom 0)]
      (dotimes [i 500]
        (when-not (= 10 (count (example-4-2)))
          (swap! *failed inc)
          #_(safe-println "#### failed ####" i)))
      (when (pos? @*failed)
        (safe-println "#### failed #### times " @*failed)))

    (catch InterruptedException _t
      (safe-println "#### main interrupted wtff ####")))

  (System/gc))



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

