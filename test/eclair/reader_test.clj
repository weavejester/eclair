(ns eclair.reader-test
  (:require [clojure.test :refer :all]
            [eclair.reader :refer :all]
            [eclair.io :as io]))

(deftest test-edn-syntax
  (is (= 1 (read-string "1")))
  (is (= 1.5 (read-string "1.5")))
  (is (= :x (read-string ":x")))
  (is (= 'x (read-string "x")))
  (is (= "x" (read-string "\"x\"")))
  (is (= [:x] (read-string "[:x]")))
  (is (= '(:x) (read-string "(:x)")))
  (is (= #{:x} (read-string "#{:x}")))
  (is (= {:x 1} (read-string "{:x 1}")))
  (is (= :x (read-string ":x ; comment")))
  (is (= {:x 1} (read-string "{:x #_2 1}"))))

(deftest test-ecl-syntax
  (is (= 1 (read-string "~x" {:vars {'x 1}})))
  (is (= {:x 1} (read-string "{~@x}" {:vars {'x [:x 1]}})))
  (is (= "1/2" (read-string "\"~{x}/~{y}\"" {:vars {'x 1, 'y 2}})))
  (is (= 1 (read-string "~(or x 1)")))
  (is (= 2 (read-string "~(or x 1)" {:vars {'x 2}})))
  (is (= 3 (read-string "~(or (or x 2) 1)" {:vars {'x 3}})))
  (is (= {:x 1} (read-string ":x 1")))
  (is (= "x" (read-string "\"\"\"x\"\"\"")))
  (is (= "\\d" (read-string "#\"\\d\"")))
  (is (= {:foo/x 1} (read-string "#:foo {:x 1}")))
  (is (= true (:x (meta (read-string "^:x {}")))))
  (is (= 1 (:x (meta (read-string "^{:x 1} {}"))))))

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
" port)}
           :example/extra
           {:text "Something extra"}})
    (io/load "eclair/config.ecl"
             {:vars {'port port, 'host host, 'dev true
                     'server-options {:x 1 :y 2}}})))
