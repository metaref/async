(ns metaref.examples.patterns
  (:require [metaref.async :as async :refer [<! >! chan go go-loop select! timeout]]
            :reload))


(comment

  (let [c1 (chan)]
    (go (>! c1 1))
    (Thread/sleep 1)
    (.tryTake c1))

  (let [c1 (chan)
        c2 (chan)]
    (go (>! c1 1))
    (go (>! c2 2))
    (println "before select")
    (select! [c1 c2]))
  
  )



;; https://go.dev/talks/2012/concurrency.slide#1

;; This pattern consist of a function that returns a channel
;; Channels are values
(defn- rand-long [n]
  (long (rand n)))

(defn boring [msg]
  (let [c (chan)]
    (go-loop [i 0]
      (>! c (str msg " " i))
      (Thread/sleep (rand-long 1000))
      (recur (inc i)))
    c))

(defn example-1 []
  (let [c (boring "boring!")]
    (dotimes [_ 5]
      (println "You say:" (<! c)))
    (println "You're boring. I'm leaving.")))

(comment

  (example-1)
  )

(defn example-2 []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 5]
      (println (<! joe))
      (println (<! ann)))
    (println "You're both boring. I'm leaving.")))

(comment

  (example-2)
  )

;; Multiplexing
;; These programs make Joe and Ann count in lockstep
;; With a fan-in function we can let whoever is ready talk
(defn fan-in [<ch1 <ch2]
  (let [c (chan)]
    (go-loop [] (>! c (<! <ch1)) (recur))
    (go-loop [] (>! c (<! <ch2)) (recur))
    c))


(defn example-3 []
  (let [c (fan-in (boring "Joe")
                  (boring "Ann"))]
    (dotimes [_ 10]
      (println (<! c)))
    (println "You're both boring. I'm leaving.")))

(comment

  (example-3)
  )


;; Restoring sequence
;; Channels are values, we can pass them around
;; Wait channel idiom

(defn boring-2 [msg]
  (let [c           (chan)
        wait-for-it (chan)]
    (go-loop [i 0]
      (>! c {:str (str msg " " i) :wait wait-for-it}) ;; sends the channel too
      (Thread/sleep (rand-long 1000))
      (<! wait-for-it) ;; Blocks until a message is received from main
      (recur (inc i)))
    c))

(defn example-4 []
  (let [c (fan-in (boring-2 "Joe")
                  (boring-2 "Ann"))]
    (dotimes [_ 5]
      (let [msg1 (<! c)
            msg2 (<! c)]
        (println (:str msg1))
        (println (:str msg2))
        (>! (:wait msg1) true)
        (>! (:wait msg2) true)))
    (println "You're both boring. I'm leaving.")))

(comment
  (example-4)
  )


;; Go's select is clojure.core.async/alts!
;; and metaref.async/select!
;; Control structure unique to concurrency
;; Another way to handle multiple channels
;; like a switch but each case is a communication

(defn fan-in-2 [<ch1 <ch2]
  (let [c (chan)]
    (go-loop []
      (let [[v _] (select! [<ch1 <ch2])]
        (>! c v))
      (recur))
    c))

(defn example-5 []
  (let [c (fan-in-2 (boring "Joe")
                    (boring "Ann"))]
    (dotimes [_ 10]
      (println (<! c)))
    (println "You're both boring. I'm leaving.")))

(comment

  (let [c1 (chan)
        c2 (chan)]
    (go (>! c1 1))
    (go (>! c2 2))
    (println "before select")
    (select! [c1 c2]))


  (example-5)
  )

;; Timeout using alt! (select!)
;; If one message takes longer than 800 msecs it timeouts
;; TODO: should be able to take from go-loop channel
;; right now the impl take waits until val is available
(defn example-6 []
  (let [c (boring "Joe")]
    (<! (go-loop []
          (let [t (timeout 800)
                [v port] (select! [c t])]
            (condp = port
              c (do (println v) (recur))
              t (println "You're to slow")))))))

(comment
  
  (example-6)
  )

;; If the whole loop takes longer than 5 sec it timeouts

(defn example-7 []
  (let [c (boring "Joe")
        t (timeout 5000)]
    (<! (go-loop []
          (let [[v port] (select! [c t])]
            (condp = port
              c (do (println v) (recur))
              t (println "You're to slow")))))))

(comment
  (example-7)
  )

;; Signaling to quit

(defn boring-3 [msg <quit]
  (let [c (chan)]
    (go-loop [i 0]
      (let [msg'  (str msg " " i)
            <put  (go (>! c msg'))
            [_ port] (select! [<put <quit])]
        (condp = port
          <put  (do
                  (Thread/sleep (rand-long 1000))
                  (recur (inc i)))

          <quit nil)))
    c))

(defn example-8 []
  (let [<quit (chan)
        c     (boring-3 "Joe" <quit)
        times (rand-int 10)]
    (println "I can only talk to you" times "times!")
    (dotimes [_ times]
      (println (<! c)))
    (>! <quit true)))

(comment
  (example-8)
  
  )

;; Handle graceful shutdown
;; round-trip communication

;; debug select! race condition
(defn boring-4 [msg quit>]
  (let [c (chan 1)]
    (go-loop [i 0]
      (let [put> (go
                   (>! c (str msg " " i)))
            [_ port] (select! [put> quit>])]
        (condp = port
          put> (do
                 (Thread/sleep (rand-long 1000))
                 (recur (inc i)))
          quit> (do
                  (println "Cleaning up!")
                  (>! quit> "See you!")))))
    c))

(defn example-9 []
  (let [quit> (chan)
        c     (boring-4 "Joe" quit>)

        times (inc (rand-int 9))]
    (println "I can only talk to you" times "times!")
    (dotimes [_ times]
      (println (<! c)))
    (>! quit> "Bye!")
    (println "Joe says:" (<! quit>))))

(comment
  (example-9)
  )

;; Daisy-chain
(defn f [left right]
  (>! left (inc (<! right))))

;; Could this be made faster?
(defn example-10 []
  (let [n         100000
        leftmost  (chan)
        channels  (repeatedly n chan)
        rightmost (reduce (fn [left right]
                            (go (f left right))
                            right)
                          leftmost
                          channels)]
    (go (>! rightmost 1))
    (println (<! leftmost))))

(comment

  (dotimes [_ 3]
    (time
     (example-10)))

  (System/gc)
;; 100001
;; "Elapsed time: 1568.687967 msecs"
;; 100001
;; "Elapsed time: 1361.467567 msecs"
;; 100001
;; "Elapsed time: 1645.39385 msecs"
;; 100001
;; "Elapsed time: 1661.914502 msecs"
;; 100001
;; "Elapsed time: 1713.627052 msecs"
;; nil

  (format "%f us" (float (* (/ 1500 100001) 1000)))
  ;; => "14.999850 us"
  ;; => ~15 us per vthread (hmm should be arround 0.2 us)
  ;; "slowest possible" task switching is ~ 1 us
  ;, fastest IO is 4 us
  )