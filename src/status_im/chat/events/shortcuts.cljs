(ns status-im.chat.events.shortcuts
  (:require [status-im.ui.screens.wallet.send.events :as send.events]
            [status-im.ui.screens.wallet.choose-recipient.events :as choose-recipient.events]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.ethereum.core :as ethereum]))

;; TODO(goranjovic) - update to include tokens in https://github.com/status-im/status-react/issues/3233
(defn- transaction-details [contact]
  (-> contact
      (select-keys [:name :address :whisper-identity])
      (assoc :symbol :ETH
             :gas (ethereum/estimate-gas :ETH))))

(defn send-shortcut-fx [db contact params]
  {:db       (-> db
                 (send.events/set-and-validate-amount-db (:amount params))
                 (choose-recipient.events/fill-request-details (transaction-details contact))
                 (navigation/navigate-to :wallet-send-transaction))
   :dispatch-n [:wallet/update-gas-price]})

(def shortcuts
  {"send" send-shortcut-fx})

(defn shortcut-override? [message]
  (get shortcuts (get-in message [:content :command])))

(defn shortcut-override-fx [db {:keys [chat-id content]}]
  (let [command              (:command content)
        contact              (get-in db [:contacts/contacts chat-id])
        shortcut-specific-fx (get shortcuts command)]
    (-> db
        (shortcut-specific-fx contact (:params content))
        (update :dispatch-n conj :cleanup-chat-command))))