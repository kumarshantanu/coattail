;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.ring
  (:require
    [clojure.string :as string]
    [coattail.internal :as i]
    [coattail.util :as u]
    [coattail.openapi :as openapi]))


(defn make-response-operators
  "Given OpenAPI responses sub-document, return a map of status codes to maps {content-type operators} as follows:
  ```
  {200      {\"application/json\" {; :tformat-writer turns :data items into type-formatted :data items
                                   :tformat-writer fw1  :default tree1  :content-writer cw1}
             \"application/xml\"  {:tformat-writer fw2  :default tree2  :content-writer cw2}}
   :default {}}
  ```"
  [responses-subdoc openapi-document openapi-toolbox]
  (reduce-kv (fn [m kstr status-subdoc]
               (let [k (if (= "default" kstr)
                         :default
                         (#?(:cljs js/parseInt
                              :clj Integer/parseInt) kstr))]
                 (if-some [content-subdoc (get status-subdoc "content")]
                   (let [rhs (reduce-kv (fn [result content-type content-type-subdoc]
                                          (if-some [schema (get content-type-subdoc "schema")]
                                            {:content-writer (or (get-in openapi-toolbox [:content-codecs
                                                                                          content-type
                                                                                          :content-writer])
                                                               (throw (ex-info "Cannot find content-writer"
                                                                        {:content-type content-type})))
                                             :default        (openapi/schema->default schema openapi-document
                                                               {:data-name "response"
                                                                :openapi-toolbox openapi-toolbox})
                                             :tformat-writer (openapi/schema->writer  schema openapi-document
                                                               {:data-name "response"
                                                                :openapi-toolbox openapi-toolbox})}
                                            result))
                               {}
                               content-subdoc)]
                     (if (empty? rhs)
                       m
                       (assoc m k rhs)))
                   m)))
    {}
    responses-subdoc))


(defn make-response-middleware
  ""
  [responses-subdoc openapi-document openapi-toolbox]
  (u/expected map? "openapi-document as a map" openapi-document)
  (u/expected map? "responses-subdoc as a map" responses-subdoc)
  (let [response? (fn [response]
                    (and (map? response)
                      (integer? (:status response))))
        operators (make-response-operators responses-subdoc openapi-document openapi-toolbox)
        statuses  (set (filter integer? (keys operators)))
        stat-msg  (str "valid HTTP status code (as per OpenAPI endpoint responses): " statuses)]
    (if (empty? operators)
      identity
      (fn [handler]
        (fn [request]
          (let [response (-> request
                           (assoc :operators operators)
                           handler)]
            ;; response: {:status 200 :data ...}
            (u/expected response? "response map with :status key set to integer HTTP status" response)
            (let [status (:status response)]
              (u/expected statuses stat-msg status)
              (if (some? (:body response))
                response
                (if-some [content-type (get-in response [:headers "Content-Type"])]
                  (let [status-operators (or (get operators status)
                                           (get operators :default))
                        allowed-ct-msgs  (->> (keys status-operators)
                                           (str "valid Content-Type header (as per OpenAPI endpoint responses): "))]
                    ;; 1. response content-type should exist in operators
                    (u/expected status-operators allowed-ct-msgs content-type)
                    (let [data (:data response)
                          {data-writer :tformat-writer
                           body-writer :content-writer} (get status-operators content-type)]
                      ;; 2. :data should be present to turn into :body
                      (u/expected some? "expected :data in endpoint handler response" data)
                      ;; 3. transform that data into :body
                      (->> data
                        data-writer
                        body-writer
                        (assoc response :body))))
                  response)))))))))


(defn make-params-middleware
  "Given a map of the form
    ```
    {param-key schema}
    ```
  return a Ring middleware to validate and supply (default) parameters."
  [ring-request-key ; e.g. :path-params
   openapi-param->path-param
   param-schema-map  ; map of {param-name param-schema-subdoc}
   openapi-document  ; reference OpenAPI document
   openapi-toolbox]
  (if (empty? param-schema-map)
    identity
    (let [param-parsers (reduce-kv (fn [pairs param-name param-schema]
                                     (assoc pairs
                                       (openapi-param->path-param param-name)
                                       (openapi/schema->parser param-schema openapi-document
                                         {:data-name ring-request-key
                                          :openapi-toolbox openapi-toolbox})))
                          {}
                          param-schema-map)
          parse-params  (fn [path-params]
                          (reduce-kv (fn [m param-key param-parser]
                                       (->> param-key
                                         (get path-params)
                                         param-parser
                                         (assoc m param-key)))
                            {}
                            param-parsers))]
      (fn params-middleware [handler]
        (fn [request]
          (let [[request error] (try
                                  [(update request ring-request-key parse-params) nil]
                                  (catch #?(:cljs js/Error
                                             :clj Exception) ex
                                    [nil {:status  405
                                          :body    (str "Validation error (path parameters): "
                                                     #?(:cljs (.-message ex)
                                                         :clj (.getMessage ^Exception ex)))
                                          :headers {"Content-type" "text/plain"}}]))]
            (if (some? request)
              (handler request)
              error)))))))


(defn make-request-body-middleware
  "Given a map of the form
    ```
    {content-type {:parser content-parser
                   :schema content-schema}}
    ```
  where
    - `content-type` is a Ring request HTTP header
    - `content-parser` is `(fn [raw-body]) -> body-content`
    - `content-schema` is OpenAPI schema
  return a Ring middleware that parses request body, validates/transforms and processes it."
  [content-handlers openapi-document openapi-toolbox]
  (let [supported-content-types (-> (keys content-handlers)
                                    (string/join ", "))
        content-handlers (reduce-kv (fn [result content-type content-details]
                                      (as-> (get content-details :schema) <>
                                        (openapi/schema->parser <> openapi-document {:data-name "requestBody"
                                                                                 :openapi-toolbox openapi-toolbox})
                                           (assoc-in result [content-type :data-parser] <>)))
                                    content-handlers
                                    content-handlers)]
    (fn [handler]
      (fn [request]
        (let [content-type (get-in request [:headers "content-type"])]
          (if-some [parser (get-in content-handlers [content-type :parser])]
            (let [body-reader (get-in content-handlers [content-type :body-reader])
                  data-parser (get-in content-handlers [content-type :data-parser])
                  content (-> request
                            body-reader
                            parser)]
              (if (nil? content)
                {:status 406
                 :body "Request body is missing"
                 :headers {"Content-type" "text/plain"}}
                (handler (data-parser content))))
            {:status 415
             :body (u/format-string "Content type '%s' is not supported. Supported: %s"
                     content-type
                     supported-content-types)
             :headers {"Content-type" "text/plain"}}))))))


(defn make-method-routes
  "Given a map of the form
    ```
    {method method-subdoc}
    ```
  where
    - `method`        is HTTP method as a lowercase string
    - `method-subdoc` is OpenAPI sub-document on the endpoint details
  return a vector of route maps:
    ```
    [{:method  method
      :id      endpoint-id
      :handler endpoint-handler}
     ...]
    ```
  See: [[todo]]"
  [handlers path-subdoc openapi-document {:keys [openapi-toolbox
                                                 openapi-method->route-method
                                                 openapi-param-name->path-param-key]
                                          :or {openapi-toolbox                    u/openapi-toolbox
                                               openapi-method->route-method       keyword
                                               openapi-param-name->path-param-key keyword}
                                          :as options}]
  (reduce-kv (fn [routes method method-subdoc]
               (let [valid-methods    #{"get" "put" "post" "delete" "options" "head" "patch" "trace"}
                     route-method     (or (valid-methods method)
                                        (throw (ex-info "Invalid method" {:expected valid-methods :found method})))
                     route-id         (or (get method-subdoc "operationId")
                                        (throw (ex-info "Missing attribute 'operationId' in endpoint"
                                                 {:method-subdoc method-subdoc})))
                     param-schema-map (fn [param-type]  ; return map of {param-name param-schema-subdoc}
                                        (u/expected #{"path" "query"} "param-type to be \"path\" or \"query\"" param-type)
                                        (as-> (get method-subdoc "parameters") <>  ; vector of maps
                                          (filter #(= param-type (get % "in")) <>)
                                          (zipmap (map #(get % "name") <>) (map #(get % "schema") <>))))
                     path-params-mw   (as-> (param-schema-map "path") <>
                                        (make-params-middleware :path-params openapi-param-name->path-param-key <>
                                          openapi-document openapi-toolbox))
                     query-params-mw  (as-> (param-schema-map "query") <>
                                        (make-params-middleware :query-params openapi-param-name->path-param-key <>
                                          openapi-document openapi-toolbox))
                     request-body-mw  (if (get-in method-subdoc ["requestBody"
                                                                    "required"])
                                        (as-> (get-in method-subdoc ["requestBody"
                                                                        "content"]) <>
                                          (reduce-kv (fn [content-handlers each-content-type schema-subdoc]
                                                       (let [codec (get-in openapi-toolbox [:content-codecs
                                                                                            each-content-type])]
                                                         (if (map? codec)
                                                           (let [body-type   (:body-type codec)
                                                                 body-reader (get-in openapi-toolbox [:body-readers
                                                                                                      body-type])
                                                                 content-schema (get schema-subdoc "schema")]
                                                             (u/expected fn? (str "body reader function for body-type "
                                                                               (pr-str body-type)) body-reader)
                                                             (when (nil? content-schema)
                                                               (u/expected (str "valid schema for content-type "
                                                                             each-content-type)))
                                                             (as-> codec <>
                                                               (assoc <> :schema content-schema)
                                                               (assoc <> :body-reader body-reader)
                                                               (assoc content-handlers each-content-type <>)))
                                                           content-handlers)))
                                            {}
                                            <>)
                                          (if (empty? <>)
                                            (u/expected (str "one or more supported content-types for method " method))
                                            <>)
                                          (make-request-body-middleware <> openapi-document openapi-toolbox))
                                        identity)
                     response-mw   (make-response-middleware (get method-subdoc "responses")
                                     openapi-document openapi-toolbox)
                     route-handler (-> (or (get handlers route-id)
                                         (throw (ex-info (str "No handler defined for route-ID " (pr-str route-id))
                                                  {:route-id route-id})))
                                       response-mw
                                       path-params-mw
                                       query-params-mw
                                       request-body-mw)]
                 (conj routes
                       {:method  (openapi-method->route-method route-method)
                        :id      route-id
                        :handler route-handler})))
    []
    path-subdoc))


(defn make-routes
  ([handlers openapi-document]
    (make-routes handlers openapi-document {}))
  ([handlers openapi-document {:keys [openapi-toolbox
                                      openapi-method->route-method
                                      openapi-param-name->path-param-key]
                               :or {openapi-toolbox                    u/openapi-toolbox
                                    openapi-method->route-method       keyword
                                    openapi-param-name->path-param-key keyword}
                               :as options}]
    (i/assert-openapi-document openapi-document)
    (reduce-kv (fn [routes each-path path-subdoc]
                 (u/expected string? "OpenAPI path string" each-path)
                 (->> (make-method-routes handlers path-subdoc openapi-document
                        {:openapi-toolbox openapi-toolbox
                         :openapi-method->route-method       openapi-method->route-method
                         :openapi-param-name->path-param-key openapi-param-name->path-param-key})
                   (array-map each-path)
                   (conj routes)))
      []
      (get openapi-document "paths"))))
