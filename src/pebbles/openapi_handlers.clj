(ns pebbles.openapi-handlers
  (:require
   [cheshire.core :as json]
   [pebbles.handlers :as handlers]
   [pebbles.interceptors :as interceptors]
   [pebbles.jwt :as jwt]
   [pebbles.openapi :as openapi]
   [io.pedestal.http.body-params :refer [body-params]]))

(defn swagger-ui-handler
  "Handler that serves the Swagger UI interface"
  []
  (fn [_request]
    {:status 200
     :headers {"Content-Type" "text/html"
               ;; Set CSP to allow CDN scripts for Swagger UI
               "Content-Security-Policy" "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data: https:; font-src 'self' https://cdn.jsdelivr.net;"}
     :body (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\">
  <title>Pebbles API Documentation</title>
  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css\">
</head>
<body>
  <div id=\"swagger-ui\"></div>
  <script src=\"https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>
  <script src=\"https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-standalone-preset.js\"></script>
  <script>
    window.onload = function() {
      window.ui = SwaggerUIBundle({
        url: \"/openapi.json\",
        dom_id: '#swagger-ui',
        deepLinking: true,
        presets: [
          SwaggerUIBundle.presets.apis,
          SwaggerUIStandalonePreset
        ],
        plugins: [
          SwaggerUIBundle.plugins.DownloadUrl
        ],
        layout: \"StandaloneLayout\"
      });
    };
  </script>
</body>
</html>")}))

(defn openapi-json-handler
  "Handler that serves the OpenAPI JSON specification"
  [db]
  (fn [_request]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string 
            (let [;; Create handler instances to extract metadata
                  handlers-with-meta
                  [{:path "/progress/:clientKrn"
                    :method :post
                    :handler (handlers/update-progress-handler db)
                    :interceptors [jwt/auth-interceptor interceptors/exception-handler 
                                  (body-params) (interceptors/validate-progress-update)]}
                   {:path "/progress/:clientKrn"
                    :method :get
                    :handler (handlers/get-progress-handler db)
                    :interceptors [interceptors/exception-handler]}
                   {:path "/health"
                    :method :get
                    :handler (handlers/health-handler)
                    :interceptors []}]
                  ;; Convert to route format for OpenAPI generation
                  routes-for-openapi
                  (mapv (fn [{:keys [path method handler interceptors]}]
                          {:path path
                           :method method
                           :interceptors (conj (vec interceptors) handler)
                           :path-params (when (.contains path ":")
                                          (reduce (fn [acc param-name]
                                                   (assoc acc (keyword param-name) :string))
                                                 {}
                                                 (map second (re-seq #":([^/]+)" path))))})
                        handlers-with-meta)]
              (openapi/generate-openapi-spec 
               routes-for-openapi
               :info {:title "Pebbles API"
                      :description "Multitenant file processing progress tracking service"
                      :version "1.0.0"}))
            {:pretty true})}))