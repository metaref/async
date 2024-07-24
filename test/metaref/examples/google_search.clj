(ns metaref.examples.google-search
  (:require [metaref.async :as async :refer [<! >! select! chan go timeout]]
            [clojure.pprint :as pp :refer [pprint]]))

;; https://go.dev/talks/2012/concurrency.slide#1

(defn- rand-long [n]
  (long (rand n)))

;; Fake a search with 100 msecs latency
(defn fake-search [kind]
  (fn [query]
    (Thread/sleep (rand-long 100))
    (str kind " result for " query)))

(def web   (fake-search "web"))
(def image (fake-search "image"))
(def video (fake-search "video"))

;; First iteration, sequencial
(defn google-1 [query]
  ((juxt web image video) query))

(defn example-1 []
  (pprint (google-1 "clojure")))

(comment
  (dotimes [_ 3]
    (time
     (example-1)))
  )

;; Making it concurrent
(defn google-2 [query]
  (let [results (atom [])
        c       (chan)]
    (go (>! c (web query)))
    (go (>! c (image query)))
    (go (>! c (video query)))
    (dotimes [_ 3]
      (swap! results conj (<! c)))
    @results))

(defn example-2 []
  (pprint (google-2 "clojure")))

(comment
  (dotimes [_ 3]
    (time
     (example-2)))
  )

;; Wait max 80ms for answers
;; gauranteed response in under 80ms
(defn google-2-1 [query]
  (let [results (atom [])
        c       (chan)
        t       (timeout 80)]
    (go (>! c (web query)))
    (go (>! c (image query)))
    (go (>! c (video query)))
    (dotimes [_ 3]
      (let [[v port] (select! [c t])]
        (when (= port t)
          (println "Timed out"))
        (swap! results conj v)))
    @results))

(defn example-2-1 []
  (pprint (google-2-1 "clojure")))

(comment
  (dotimes [_ 3]
    (time
     (example-2-1)))
  )

;; But you can avoid timeouts by adding replication
;; First define de fastest function
(defn fastest [query & replicas]
  (let [c              (chan)
        search-replica (fn [replica]
                         (go (>! c (replica query))))]
    (run! search-replica replicas)
    (<! c)))

(defn use-fastest []
  (pprint
   (fastest "clojure"
            (fake-search "replica-1")
            (fake-search "replica-2"))))

(comment
  (time
   (use-fastest))
  )

;; Putting it all together
(def web1   (fake-search "web-1"))
(def image1 (fake-search "image-1"))
(def video1 (fake-search "video-1"))

(def web2   (fake-search "web-2"))
(def image2 (fake-search "image-2"))
(def video2 (fake-search "video-2"))

(defn google-3 [query]
  (let [results (atom [])
        c       (chan)
        t       (timeout 80)]
    (go (>! c (fastest query web1 web2)))
    (go (>! c (fastest query image1 image2)))
    (go (>! c (fastest query video1 video2)))
    (dotimes [_ 3]
      (let [[v port] (select! [c t])]
        (when (= port t)
          (println "Timed out"))
        (swap! results conj v)))
    @results))

(defn example-3 []
  (pprint (google-3 "clojure")))

(comment
  (dotimes [_ 3]
    (time
     (example-3)))
  )

;; Converted a slow, sequential, failure-sensitive program into a
;; fast, concurrent, replicated, robust one
;; These tools are better suited to express complex operations dealing with:
;; multiple inputs
;; multiple outputs
;; timeouts
;; failure