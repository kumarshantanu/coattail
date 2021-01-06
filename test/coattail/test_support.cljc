;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns coattail.test-support
  #?(:cljs (:require-macros [coattail.test-macros :refer [inline-resource inline-file]]))
  (:require
    #?(:clj [coattail.test-macros :refer [inline-resource inline-file]])
    [coattail.util :as u]))


(def json-string->data (get-in u/openapi-toolbox [:content-codecs
                                                  "application/json"
                                                  :content-parser]))
(def data->json-string (get-in u/openapi-toolbox [:content-codecs
                                                  "application/json"
                                                  :content-writer]))


(def petstore3-openapi (-> "external/OpenAPI-Specification/examples/v3.0/petstore.json"
                         inline-file
                         json-string->data))
