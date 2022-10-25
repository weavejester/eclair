(ns eclair.reader-test
  (:require [clojure.test :refer :all]
            [eclair.reader :refer :all]))

(deftest test-edn-syntax
  (is (= 1 (read-string "1")))
  (is (= 1.5 (read-string "1.5")))
  (is (= :x (read-string ":x")))
  (is (= 'x (read-string "x")))
  (is (= "x" (read-string "\"x\"")))
  (is (= [:x] (read-string "[:x]")))
  (is (= '(:x) (read-string "(:x)")))
  (is (= #{:x} (read-string "#{:x}")))
  (is (= {:x 1} (read-string "{:x 1}"))))
