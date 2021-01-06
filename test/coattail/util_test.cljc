;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.util-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    #?(:cljs [coattail.util :as u :include-macros true]
        :clj [coattail.util :as u])
    ))


(deftest test-base64-encode
  (is (= ""
        (u/string->base64 "")))
  (is (= "aGVsbG8="
        (u/string->base64 "hello"))))


(deftest test-base64-decode
  (is (= "" (u/base64->string "")))
  (is (= "hello" (u/base64->string "aGVsbG8="))))


;; ----- RFC 3339 (Internet date/time) -----


(deftest test:parse-rfc3339-full-date
  (testing "happy full-date"
    (is (= #inst "2020-12-21T00:00:00.000-00:00"
          (u/parse-rfc3339-full-date "2020-12-21"))))
  (testing "bad full-date"
    (is (thrown? #?(:cljs js/Error
                     :clj NullPointerException)
          (u/parse-rfc3339-full-date nil)) "nil input")
    (is (thrown? #?(:cljs ExceptionInfo
                     :clj RuntimeException)
          (u/parse-rfc3339-full-date "")) "empty-string input")
    (is (thrown? #?(:cljs ExceptionInfo
                     :clj RuntimeException)
          (u/parse-rfc3339-full-date "hello there")) "non date-time string input input"))
  )


(deftest test:parse-rfc3339-date-time
  (testing "happy date-time"
    (is (= #inst "2020-12-21T02:13:25.000-00:00"
          (u/parse-rfc3339-date-time "2020-12-21T02:13:25")) "parse minimal date-time, no timezone")
    (is (= #inst "2020-12-21T02:13:25.123-00:00"
          (u/parse-rfc3339-date-time "2020-12-21T02:13:25.123Z")) "parse date-time with fractional seconds, UTC"))
  (testing "bad date-time"
    (is (thrown? #?(:cljs js/Error
                     :clj NullPointerException)
          (u/parse-rfc3339-date-time nil)) "nil input")
    (is (thrown? #?(:cljs ExceptionInfo
                     :clj RuntimeException)
          (u/parse-rfc3339-date-time "")) "empty-string input")
    (is (thrown? #?(:cljs ExceptionInfo
                     :clj RuntimeException)
          (u/parse-rfc3339-date-time "hello there")) "non date-time string input input")))


(deftest test:write-rfc3339-full-date
  (testing "happy full-date"
    (is (= "2020-12-21"
          (u/write-rfc3339-full-date #inst "2020-12-21T00:00:00.000-00:00")))
    (is (= "2020-12-20"
          (u/write-rfc3339-full-date #inst "2020-12-21T00:00:00.000+05:30"))))
  (testing "bad full-date"
    (is (thrown? #?(:cljs js/Error
                     :clj NullPointerException)
          (u/write-rfc3339-full-date nil)))))


(deftest test:write-rfc3339-date-time
  (testing "happy date-time"
    (is (= "2020-12-21T02:13:25.000Z"
          (u/write-rfc3339-date-time  #inst "2020-12-21T02:13:25.000-00:00")))
    (is (= "2020-12-21T02:13:25.123Z"
          (u/write-rfc3339-date-time  #inst "2020-12-21T02:13:25.123-00:00")))
    (is (= "2020-12-20T20:43:25.123Z"
          (u/write-rfc3339-date-time  #inst "2020-12-21T02:13:25.123+05:30"))))
  (testing "bad date-time"
    (is (thrown? #?(:cljs js/Error
                     :clj NullPointerException)
          (u/write-rfc3339-date-time nil)))))
