;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.ring-test
  (:require
    #?(:cljs [cljs.test    :refer-macros [deftest is testing]]
        :clj [clojure.test :refer        [deftest is testing]])
    [clojure.pprint :as pp]
    [clojure.walk   :as walk]
    [coattail.ring :as r]
    [coattail.test-support :as ts]))


(defn ok [request]
  {:status 200
   :body "OK"})


(def handlers {"listPets"   ok
               "createPets" ok
               "showPetById" ok})


(deftest test-ring
  (let [routes (r/make-routes handlers ts/petstore3-openapi)]
    (pp/pprint (walk/postwalk (fn [m]
                                (if (and (map? m) (contains? m :handler))
                                  (dissoc m :handler)
                                  m))
                 routes))
    (is (vector? routes))
    (is (= (count handlers) (reduce (fn [a x]
                                      (+ a (count (val (first x)))))
                              0
                              routes)))))
