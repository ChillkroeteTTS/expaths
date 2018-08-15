(ns tourme.firebase.firebase-mock
  (:require [tourme.firebase.firebase-prtcl :as prtcl]
            [tourme.debug-data :as dd]
            [re-frame.core :as rf]))

(def tours (atom nil))
(def artificial-rq-timeout 1500)
(def bug-reports (atom []))

(defn reload-rf-tours! []
  (swap! re-frame.db/app-db (fn [o] (assoc o :tours @tours))))

(defn call-success [on-success]
  (js/setTimeout (fn [] (on-success))
                 artificial-rq-timeout))

(defrecord FirebaseMock []
  prtcl/Firebase
  (setup! [this]
    (reset! tours dd/default-tours))

  (merge-document! [this id tour on-success on-failure]
    (js/setTimeout (fn []
                     (swap! tours #(assoc % id tour))
                     (reload-rf-tours!)
                     (on-success))
                   artificial-rq-timeout))

  (fetch-and-wire-tours! [this fetch-center tour-fetch-radius-in-km on-success on-failure]
    (js/setTimeout (fn []
                     (reload-rf-tours!)
                     (on-success))
                   artificial-rq-timeout))

  (add-tour! [this tour on-success on-failure]
    (let [new-id (str (inc (cljs.reader/read-string (last (sort (keys @tours))))))]
      (js/setTimeout (fn []
                       (swap! tours #(assoc % new-id (assoc tour :id new-id)))
                       (reload-rf-tours!)
                       (on-success))
                     artificial-rq-timeout)))

  (delete-tour! [this id on-success on-failure]
    (js/setTimeout (fn []
                     (swap! tours #(dissoc % id))
                     (reload-rf-tours!)
                     (on-success))
                   artificial-rq-timeout))

  (save-bug-report! [this state logs msg]
    (js/setTimeout (fn [] (swap! bug-reports #(conj % {:state state :logs logs :msg msg})))
                   artificial-rq-timeout))

  (sign-in-social! [this provider on-success on-failure]
    (js/setTimeout (fn []
                     (rf/dispatch [:set-user dd/default-user])
                     (on-success))
                   artificial-rq-timeout))

  (sign-in-anonymously! [this on-success on-failure]
    (js/setTimeout (fn []
                     (rf/dispatch [:set-user dd/anonymous-user])
                     (on-success))
                   artificial-rq-timeout))

  (accepted-policies! [this user-id on-success on-failure]
    (js/setTimeout (partial on-success true) artificial-rq-timeout))

  (accept-policies! [this user-id on-success on-failure]
    (js/setTimeout on-success artificial-rq-timeout))

  (sign-in! [_ email password on-success on-failure]
    (js/setTimeout
      (fn []
        (if (and (= email "a")
                 (= password "a"))
          (do (rf/dispatch [:set-user dd/default-user])
              (on-success))
          (on-failure :invalid-credentials)))
      artificial-rq-timeout))

  (sign-in-known-user! [_]
    (rf/dispatch-sync [:set-user dd/default-user]))

  (sign-up! [_ email password on-success on-failure]
    (call-success on-success))

  (reset-password! [_ email on-success on-failure]
    (call-success on-success))

  (sign-out! [_ ] ))
