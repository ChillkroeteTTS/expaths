(ns tourme.run-tests
  (:require [tourme.migration.migration-test]
            [cljs.nodejs :as node]))

(defn run-tourme-tests []
  (cljs.test/run-tests
    'tourme.migration.migration-test))

(enable-console-print!)
(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (do
      (prn "tests succeeded, exit with 0")
      (.exit node/process 0))
    (do
      (prn "tests failed, exit with 1")
      (.exit node/process 1))))

(run-tourme-tests)