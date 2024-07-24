(ns metaref.async-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [metaref.async :as async :refer :all]))

(deftest async-test
  (testing "hello world"
    (let [msg "Hello, World!"
          ch  (chan)
          ret (go (>! ch msg))] 
      (is (= msg  (<! ch)))
      (is (= true (<! ret)))))
  
  (testing "async/select!"
    
    (let [_ (stest/instrument)]
      (is (nil? (:check-failed (-> `select!
                                   stest/check
                                   stest/summarize-results)))))))


(comment 
  
  (test/run-test async-test)

  (stest/unstrument)

  )
