;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.openapi-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    [clojure.pprint :as pp]
    [coattail.openapi :as openapi]
    [coattail.test-support :as ts]))


(deftest test-parser
  (let [order-schema (get-in ts/petstore3-openapi ["components"
                                                   "schemas"
                                                   "Pet"])]
    ;;(println "::::::::" (type ))
    ;;(pp/pprint (keys order-schema))
    (prn (openapi/schema->default order-schema ts/petstore3-openapi {:data-name "test"}
           ))))
