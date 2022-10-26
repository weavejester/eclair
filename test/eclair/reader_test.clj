(ns eclair.reader-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
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

(deftest test-load-ecl-file
  (let [port 8080
        host "localhost"
        url  (format "http://%s:%s/example" host port)]
    (is = {:example/server {:port port, :url url, :x 1, :y 2}
           :example/stream {:filter #"\d+", :url url}
           :example/text
           {:environment "development"
            :description (format "
Some long description
Multiple lines
With \"Inner quotations\"
And maybe a variable like %s.
" port)}})
    (read-string (slurp (io/resource "eclair/config.ecl"))
                 {:vars {'port port, 'host host, 'dev true
                         'server-options {:x 1 :y 2}}})))

