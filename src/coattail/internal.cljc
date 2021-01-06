;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.internal
  (:require
    [coattail.util :as u]))


;; ----- predicates -----


(def ^{:arglists '([key])} map-containing
  "Return a predicate that tests for a map containing given key."
  (memoize (fn [k & more]
             (fn [m]
               (and (map? m)
                 (some #(contains? m %) (cons k more)))))))


;; ----- assertions -----

(defn assert-openapi-document
  [openapi-document]
  (u/expected
    (map-containing "openapi")
    "OpenAPI document (map containing \"openapi\" key)" openapi-document))


(defn assert-openapi-schema
  [openapi-schema]
  (u/expected
    (map-containing "type" "$ref")
    "OpenAPI schema document containing \"type\" or \"$ref\" key" openapi-schema))
