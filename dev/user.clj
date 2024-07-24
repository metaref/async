(ns user)

(comment
  ;; Instrument all functions with spec
  (do ;; with :test alias
    (require '[clojure.spec.test.alpha :as stest])
    (stest/instrument))

  (do ;; with :debug alias
    (require '[flow-storm.api :as fs-api])
    (fs-api/local-connect))

  ) 


