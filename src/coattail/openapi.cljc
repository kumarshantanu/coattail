;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.openapi
  "Support for three operator types:
  :parser
  :writer
  :sample
  See: [[schema->sample]], [[schema->parser]], [[schema->writer]]"
  (:require
    #?(:clj [clojure.main :refer [demunge]])
    [clojure.string :as string]
    [coattail.internal :as i]
    [coattail.util :as u]))


(def ^:dynamic *operator-type* nil)


(def ^:dynamic *openapi-document* nil)


(def ^:dynamic *openapi-toolbox* u/openapi-toolbox)


(defn core-schema->operator
  [data-name openapi-schema]
  (i/assert-openapi-schema openapi-schema)
  (let [;; schema items
        data-type     (get openapi-schema "type")
        data-format   (get openapi-schema "format")
        data-enum     (get openapi-schema "enum")
        data-default  (get openapi-schema "default")
        ;; openapi-toolbox items
        expectant     (get *openapi-toolbox* :expectant)
        type-pred     (get-in *openapi-toolbox* [:core-types data-type :pred])
        format-parser (get-in *openapi-toolbox* [:core-types data-type :format data-format :parser])
        format-writer (get-in *openapi-toolbox* [:core-types data-type :format data-format :writer])
        format-sample (if (some? data-default)
                        (format-parser data-default)
                        (get-in *openapi-toolbox* [:core-types data-type :format data-format :sample]))
        ;; other items
        pred-verify   (fn [value pred msg]
                        (if (pred value)
                          value
                          (expectant msg value)))
        parse-format  (fn [value]
                        (try
                          (format-parser value)
                          (catch #?(:cljs js/Error
                                     :clj Exception) ex
                            (expectant (u/format-string "%s to be valid %s format (Error: %s)"
                                         data-name
                                         data-format
                                         (.getMessage ex))
                              value))))
        write-format  (fn [value]
                        (try
                          (format-writer value)
                          (catch #?(:cljs js/Error
                                     :clj Exception) ex
                            (expectant (u/format-string "%s to be a valid value for %s format (Error: %s)"
                                         data-name
                                         data-format
                                         (.getMessage ex))
                              value))))
        else-default  (if (contains? openapi-schema "default")
                        (fn [x] (if (nil? x)
                                  data-default
                                  x))
                        identity)
        ensure-enum   (if (some? data-enum)
                        (let [pred #(contains? (set data-enum) %)]
                          (fn [x]
                            (expectant pred (str "value to be one of enum values " (string/join ", " data-enum)) x)
                            x))
                        identity)
        type-pred-msg (str data-name " value to comply with type predicate " (-> type-pred str demunge))]
    (u/expected fn? "expectant to be a function" expectant)
    (u/expected fn? "type-pred to be a function" type-pred)
    (u/expected fn? "format-parser to be a function" format-parser)
    (u/expected fn? "format-writer to be a function" format-writer)
    (case *operator-type*
      :sample format-sample
      :parser (fn value-parser [value]
                (-> value
                  (pred-verify type-pred type-pred-msg)
                  else-default
                  parse-format
                  ensure-enum))
      :writer (fn value-writer [value]
                (-> value
                  write-format
                  else-default
                  ensure-enum
                  (pred-verify type-pred type-pred-msg)))
      (u/expected "operator-type to be either :sample, :parser or :writer" *operator-type*))))


(declare schema->operator)


(defn object-schema->operator
  [object-name openapi-schema]
  (let [;; schema items
        object-properties   (get openapi-schema "properties")
        required-properties (get openapi-schema "required")
        ;; openapi-options items
        expectant           (get *openapi-toolbox* :expectant)
        ;; other items
        property-operators  (reduce-kv (fn [operators each-property each-schema]
                                         (->> each-schema
                                           (schema->operator (u/format-string "%s/%s" object-name each-property))
                                           (assoc operators each-property)))
                              {}
                              object-properties)
        expected-object-msg (str object-name " to be a map")]
    (if (= :sample *operator-type*)
      property-operators
      (fn object-operator [object]
        (expectant map? expected-object-msg object)
        (doseq [each-property required-properties]
          (when-not (contains? object each-property)
            (expectant (str "required property " (pr-str each-property)))))
        (reduce-kv (fn [result each-property each-operator]
                     (if (contains? object each-property)
                       (->> each-property
                         (get object)
                         each-operator
                         (assoc result each-property))
                       result))
          {}
          property-operators)))))


(defn array-schema->operator
  [array-name openapi-schema]
  (let [item-schema (get openapi-schema "items")
        item-op'tor (schema->operator (str array-name "[n]") item-schema)
        ;; toolbox items
        expectant   (get *openapi-toolbox* :expectant)
        ;; other items
        array-msg   (str array-name " to be a vector (array) of elements")]
    (if (= :sample *operator-type*)
      [item-op'tor]
      (fn array-operator [array]
        (expectant vector? array-msg array)
        (mapv item-op'tor array)))))


(defn schema->operator
  ([data-name openapi-schema]
    (let [;; schema items
          schema-type (get openapi-schema "type")
          ;; toolbox items
          expectant   (get *openapi-toolbox* :expectant)
          ref-marker  "$ref"
          ref-lookup  (fn ref-lookup* [^String hash-path]
                        (let [path (subs hash-path 1)
                              path-tokens (next (string/split path #"/"))]
                          (let [ref-schema (get-in *openapi-document* (vec path-tokens))]
                            (u/expected some? (str "valid referenced schema at " path) ref-schema)
                            ref-schema)))]
      (cond
        (contains? openapi-schema
          ref-marker)            (->> ref-marker
                                   (get openapi-schema)
                                   ref-lookup
                                   (schema->operator      data-name))
        (= schema-type "object") (object-schema->operator data-name openapi-schema)
        (= schema-type "array")  (array-schema->operator  data-name openapi-schema)
        :else                    (core-schema->operator   data-name openapi-schema))))
  ([operator-type data-name openapi-schema openapi-document openapi-toolbox]
    (binding [*operator-type*    operator-type
              *openapi-document* openapi-document
              *openapi-toolbox*  openapi-toolbox]
      (schema->operator data-name openapi-schema))))


(defn schema->sample
  ([openapi-schema openapi-document]
    (schema->sample openapi-schema openapi-document {}))
  ([openapi-schema openapi-document {:keys [data-name openapi-toolbox]
                                     :or {data-name "data"
                                          openapi-toolbox u/openapi-toolbox}
                                     :as options}]
    (i/assert-openapi-document openapi-document)
    (i/assert-openapi-schema   openapi-schema)
    (schema->operator :sample data-name openapi-schema openapi-document openapi-toolbox)))


(defn schema->parser
  ([openapi-schema openapi-document]
    (schema->parser openapi-schema openapi-document {}))
  ([openapi-schema openapi-document {:keys [data-name openapi-toolbox]
                                     :or {data-name "data"
                                          openapi-toolbox u/openapi-toolbox}
                                     :as options}]
    (i/assert-openapi-document openapi-document)
    (i/assert-openapi-schema   openapi-schema)
    (schema->operator :parser data-name openapi-schema openapi-document openapi-toolbox)))


(defn schema->writer
  ([openapi-schema openapi-document]
    (schema->parser openapi-schema openapi-document {}))
  ([openapi-schema openapi-document {:keys [data-name openapi-toolbox]
                                     :or {data-name "data"
                                          openapi-toolbox u/openapi-toolbox}
                                     :as options}]
    (i/assert-openapi-document openapi-document)
    (i/assert-openapi-schema   openapi-schema)
    (schema->operator :writer data-name openapi-schema openapi-document openapi-toolbox)))
