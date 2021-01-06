;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.util
  "Utility functions to support this library."
  (:require
    #?@(:cljs [[goog.string :as gs]
               goog.string.format
               goog.date.UtcDateTime
               goog.date.Date]
         :clj [[clojure.instant :as ins]])
    #?(:cljs [goog.crypt.base64 :as base64])
    [#?(:cljs cljs.reader
         :clj clojure.edn) :as edn]
    [clojure.string :as string])
  #?(:clj (:import
            [java.sql Timestamp]
            [java.text SimpleDateFormat]
            [java.util Base64 Date UUID])))


;; ----- string formatting -----


(def ^{:arglists '([fmt & args])
       :doc "Same as clojure.core/format"} format-string #?(:cljs (fn [fmts & args]
                                                                    (apply gs/format fmts args))
                                                             :clj format))


;; ----- input check -----


(defn expected
  ([expectation-msg]
    (throw (ex-info (str "Expected " expectation-msg) {})))
  ([expectation-msg value]
    (throw (ex-info (format-string "Expected %s, but found %s" expectation-msg (pr-str value))
             {:value value
              :type (type value)})))
  ([pred expectation-msg value]
    (when-not (pred value)
      (expected expectation-msg value))))


;; ----- Base64 conversion -----


(defn bytes->base64
  "Encode given byte-array (or vector) into a Base64 string."
  ^String [^bytes bs]
  (let [bs-array (if (#?(:cljs array?
                          :clj bytes?) bs)
                   bs
                   (#?(:cljs to-array
                        :clj byte-array) bs))]
    #?(:cljs (base64/encodeByteArray bs-array)
        :clj (let [b64e (Base64/getEncoder)]
               (.encodeToString b64e bs-array)))))


(defn string->base64
  "Encode given string into Base64 string."
  [^String input]
  #?(:cljs (base64/encodeString input)
      :clj (bytes->base64 (.getBytes input))))


(defn base64->bytes
  ^bytes [^String input]
  #?(:cljs (base64/decodeStringToByteArray input)
      :clj (let [b64d (Base64/getDecoder)]
             (.decode b64d input))))


(defn base64->string
  "Decode Base64 string into a regular string."
  [^String input]
  #?(:cljs (base64/decodeString input)
      :clj (String. (base64->bytes input))))


;; ----- token -----


#?(:clj (defn random-uuid
          ^String []
          (str (UUID/randomUUID))))


(defn create-token
  ^String []
  (-> (random-uuid)
    string->base64))


(defn parse-token
  ^String [^String token]
  (base64->string token))


;; ----- RFC 3339 (Internet date/time) -----


(defn parse-rfc3339-full-date
  ^Date [^String rfc3339-full-date]
  #?(:cljs (if-some [[_ y m d] (re-find #"(\d{4})-(\d{2})-(\d{2})" rfc3339-full-date)]
             ;; (goog.date.Date. (long y) (dec (long m)) (long d))
             (js/Date. (long y) (dec (long m)) (long d))
             (throw (ex-info "Cannot parse RFC 3339 full-date" {:token rfc3339-full-date})))
      :clj (ins/read-instant-date rfc3339-full-date)))


(defn parse-rfc3339-date-time
  ^Date [^String rfc3339-date-time]
  #?(:cljs (if-some [match (re-find #"(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2}):(\d{2})(?:\.(\d{3}))?"
                             rfc3339-date-time)]
             ;;(goog.date.UtcDateTime.fromIsoString token)
             (let [[___ yyyy mm dd
                    hh MM ss ms tz] match]
               (js/Date. (long yyyy) (dec (long mm)) (long dd) (long hh) (long MM) (long ss) (long (or ms 0))))
             (throw (ex-info "Cannot parse RFC 3339 date-time" {:token rfc3339-date-time})))
      :clj (ins/read-instant-timestamp rfc3339-date-time)))


(defn write-rfc3339-full-date
  ^String [^Date date]
  #?(:cljs (str (.getUTCFullYear date)
             "-" (gs/padNumber (inc (.getUTCMonth date))  2)
             "-" (gs/padNumber (.getUTCDate date)         2))
      :clj (let [sdf (SimpleDateFormat. "yyyy-MM-dd")]
             (.format sdf date))))


(defn write-rfc3339-date-time
  ^String [^Date date]
  #?(:cljs (str (.getUTCFullYear date)
             "-" (gs/padNumber (inc (.getUTCMonth date))  2)
             "-" (gs/padNumber (.getUTCDate date)         2)
             "T" (gs/padNumber (.getUTCHours date)        2)
             ":" (gs/padNumber (.getUTCMinutes date)      2)
             ":" (gs/padNumber (.getUTCSeconds date)      2)
             "." (gs/padNumber (.getUTCMilliseconds date) 3)
             "Z")
      :clj (let [sdf (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")]
             (.format sdf date))))


;; ----- toolbox -----


(def openapi-toolbox
  "A super-tree of all overridable tools for OpenAPI parsing and writing."
  (let [not-implemented (fn not-implemented [path]
                          (fn [_]
                            (throw (ex-info (str "Not implemented. Override " path " to get functionality") {}))))
        resolve-var  (fn resolve-var ; CLJ only: Resolve and return a var, else `nil` on failure. 
                       [qualified-symbol]
                       (expected (every-pred symbol? #(some? (namespace %))) "qualified symbol" qualified-symbol)
                       #?(:cljs (throw (ex-info "Not implemented" {:var-name qualified-symbol}))
                           :clj (or (resolve qualified-symbol)
                                  (do
                                    (try
                                      (require (symbol (namespace qualified-symbol)))
                                      (catch Exception _))
                                    (resolve qualified-symbol)))))
        body->string (fn body->string [body]
                       (cond
                         (string? body) body
                         (seq? body)    (apply str body)
                         :else          #?(:cljs (throw (ex-info "Do not know how to read body" {:body body}))
                                            :clj (slurp body))))]
    {; (fn [expectation-msg] [expectation-msg value] [pred expectation-msg value]) -> throws exception on error, else nil
     :expectant expected
     :core-types {"string"  {:pred   string?
                             :format {nil         {:parser identity                :writer str                     :default ""}
                                      "byte"      {:parser base64->bytes           :writer bytes->base64           :default [1 2]}
                                      "binary"    {:parser identity                :writer str                     :default ""}
                                      "date"      {:parser parse-rfc3339-full-date :writer write-rfc3339-full-date :default #inst "2017-08-23T00:00:00"}
                                      "date-time" {:parser parse-rfc3339-date-time :writer write-rfc3339-date-time :default #inst "2017-08-23T10:22:22"}
                                      "password"  {:parser identity                :writer str                     :default "s3cr3tp455w0rd"}}}
                  "integer" {:pred   integer?
                             :format {"int32"     {:parser int      :writer str :default 0}
                                      "int64"     {:parser long     :writer str :default 0}}}
                  "number"  {:pred   float?
                             :format {"float"     {:parser float    :writer str :default 0.0}
                                      "double"    {:parser double   :writer str :default 0.0}}}
                  "boolean" {:pred   boolean?
                             :format {nil         {:parser identity :writer str :default false}}}}
     ;; Ring integration stuff
     :body-readers {:string body->string}
     :content-codecs {"application/edn"  {:body-type      :string
                                          :content-parser edn/read-string
                                          :content-writer pr-str}
                      ;; other common types you may want to override
                      "application/json" {:body-type      :string
                                          :content-parser #?(:cljs (fn [json-string] (->> json-string
                                                                                       (.parse js/JSON)
                                                                                       js->clj))
                                                              :clj (or
                                                                     (resolve-var 'jsonista.core/read-value)
                                                                     (resolve-var 'cheshire.core/parse-string)
                                                                     (resolve-var 'clojure.data.json/read-str)
                                                                     (not-implemented [:content-codecs
                                                                                       "application/json"
                                                                                       :content-parser])))
                                          :content-writer #?(:cljs (fn [cljs-data] (->> cljs-data
                                                                                     clj->js
                                                                                     (.stringify js/JSON)))
                                                              :clj (or
                                                                     (resolve-var 'jsonista.core/write-value-as-string)
                                                                     (resolve-var 'cheshire.core/generate-string)
                                                                     (resolve-var 'clojure.data.json/write-str)
                                                                     (not-implemented [:content-codecs
                                                                                       "application/json"
                                                                                       :content-writer])))}
                      "application/xml"  {:body-type      :string
                                          :content-parser (not-implemented [:content-codecs
                                                                            "application/xml"
                                                                            :content-parser])
                                          :content-writer (not-implemented [:content-codecs
                                                                            "application/xml"
                                                                            :content-writer])}
                      "application/x-www-form-urlencoded" {:body-type      :string
                                                           :content-parser (not-implemented
                                                                             [:content-codecs
                                                                              "application/x-www-form-urlencoded"
                                                                              :content-parser])}}}))
