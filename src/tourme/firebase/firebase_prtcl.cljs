(ns tourme.firebase.firebase-prtcl)

(defprotocol Firebase
  (setup! [this])
  (merge-document! [this id tour on-success on-failure])
  (fetch-and-wire-tours! [this fetch-center tour-fetch-radius-in-km on-success on-failure])
  (add-tour! [this tour on-success on-failure])
  (delete-tour! [this id on-success on-failure])
  (save-bug-report! [this state logs msg])

  (reset-password! [this email on-success on-failure])
  (accepted-policies! [this user-id on-success on-failure])
  (accept-policies! [this user-id on-success on-failure])
  (sign-in! [this email password on-success on-failure])
  (sign-in-social! [this provider on-success on-failure])
  (sign-in-anonymously! [this on-success on-failure])
  (sign-in-known-user! [this])
  (sign-up! [this email password on-success on-failure])
  (sign-out! [this]))