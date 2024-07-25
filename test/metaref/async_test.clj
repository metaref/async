(ns metaref.async-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [metaref.async :as async :refer :all]))

(deftest async-test
  (testing "hello world"
    (let [msg "Hello, World!"
          ch  (chan)
          ret (go (>! ch msg))]
      (is (= msg  (<! ch)))
      (is (= true (<! ret))))))



(comment

  (test/run-test async-test)
  )
