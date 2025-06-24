(ns pebbles.test-system
  "Test version of system that doesn't rely on environment variables"
  (:require
   [com.stuartsierra.component :as component]
   [pebbles.system :as system]
   [pebbles.sqs-consumer :as sqs]
   [pebbles.kafka-consumer :as kafka]))

(defn test-system
  "Create a test system with explicit configuration"
  [{:keys [sqs-enabled? kafka-enabled? mongo-uri port
           sqs-queue-url aws-region
           kafka-bootstrap-servers kafka-group-id kafka-topic-name]
    :or {mongo-uri "mongodb://localhost:27017/test"
         port 8081}}]
  (cond-> (component/system-map
           :mongo (system/map->MongoComponent {:uri mongo-uri})
           :http  (component/using
                   (system/map->HttpComponent {:port port})
                   [:mongo]))
    
    sqs-enabled?
    (assoc :sqs-consumer (component/using
                          (sqs/sqs-consumer
                           sqs-queue-url
                           aws-region)
                          [:mongo]))
    
    kafka-enabled?
    (assoc :kafka-consumer (component/using
                            (kafka/make-kafka-consumer
                             {:bootstrap-servers kafka-bootstrap-servers
                              :group-id kafka-group-id
                              :topic-name kafka-topic-name})
                            [:mongo]))))