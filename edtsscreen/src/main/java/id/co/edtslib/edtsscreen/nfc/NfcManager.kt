package id.co.edtslib.edtsscreen.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import id.co.edtslib.edtsds.popup.Popup
import id.co.edtslib.edtsds.popup.PopupDelegate

class NfcManager(private val activity: FragmentActivity, intent: Intent) {
    interface NfcManagerDelegate {
        fun onRead(messages: Array<NdefMessage?>)
        /**
         * txBytes: Byte Array of transmitter command
         * rxBytes: Byte Array of receiver result
         * */
        fun onCommandReceived(txBytes: ByteArray, rxBytes: ByteArray)
        fun openSetting(popup: Popup)
    }

    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var pendingIntent: PendingIntent = PendingIntent.getActivity(
        activity, 0, intent
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE
    )

    var isoDep: IsoDep? = null

    var delegate: NfcManagerDelegate? = null

    fun checkNfcFeature(callback: () -> Unit) {
        Utils.checkNfcStatus(nfcAdapter, {
            Popup.show(
                activity = activity,
                title = "Fitur NFC",
                message = "Ponsel anda tidak mendukung fitur NFC.",
                positiveButton = "Tutup",
                positiveClickListener = null
            )
        }, {
            callback.invoke()
        }, {
            Popup.show(
                activity = activity,
                title = "Fitur NFC",
                message = "Aktifkan fitur NFC pada ponsel anda.",
                positiveButton = "Pengaturan",
                negativeButton = "Batal",
                positiveClickListener = object : PopupDelegate {
                    override fun onClick(popup: Popup, view: View) {
                        /** Open NFC Setting on Android phone */
                        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                        activity.startActivity(intent)
                        popup.dismiss()
                    }
                },
                negativeClickListener = null
            )
        })
    }

    fun dispatch() {
        //if (!nfcAdapter.isEnabled) showWirelessSettings()
        nfcAdapter?.enableForegroundDispatch(activity,
            pendingIntent,
            null,
            null)
    }

    fun processIntent(intent: Intent?, bytes: ByteArray? = null) {
        if (intent != null) {
            activity.intent = intent
            resolveIntent(intent, bytes)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveIntent(intent: Intent, bytes: ByteArray? = null) {
        val action = intent.action

        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val msgs: Array<NdefMessage?>
            if (rawMsgs != null) {
                msgs = arrayOfNulls(rawMsgs.size)
                for (i in rawMsgs.indices) {
                    msgs[i] = rawMsgs[i] as NdefMessage
                }

                delegate?.onRead(msgs)
            } else {
                val empty = ByteArray(0)
                val id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
                val tag = intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag?
                if (tag != null) {
                    val nfcData = dumpTagData(tag)

                    val record = NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, Gson().toJson(nfcData).toByteArray())
                    val msg = NdefMessage(arrayOf(record))
                    msgs = arrayOf(msg)

                    delegate?.onRead(msgs)

                    if (bytes != null) {
                        processIsoDep(tag, bytes)
                    }

                }
            }
        }
    }

    fun processIsoDep(tag: Tag, bytes: ByteArray?) {
        isoDep = IsoDep.get(tag)
        isoDep?.connect()
        isoDep?.timeout = 5000 // 5 sec time out

        val isConnected = isoDep?.isConnected
        if (isConnected == true && bytes != null) {
            sendCommand(bytes)
        } else {
            Log.e("NfcManager", "NFC not connected")
        }
    }

    fun closeConnection() {
        isoDep?.close()
    }

    fun sendCommand(bytes: ByteArray) {
        if (isoDep != null) {
            try {
                val lastBalanceBytes = isoDep!!.transceive(bytes)
                delegate?.onCommandReceived(bytes, lastBalanceBytes)
            } catch (err: Exception) {
                Log.e("NfcManager", "error=${err.message}")
            }
        }
    }

    private fun dumpTagData(tag: Tag): NfcData {
        val hex = Utils.toHex(tag.id)
        val reversedHex = Utils.toReversedHex(tag.id)
        val id = Utils.toDec(tag.id)
        val reversedId = Utils.toReversedDec(tag.id)

        return NfcData(hex = hex,
            reversedHex = reversedHex,
            id = id,
            reversedId = reversedId,
            techList = tag.techList.toList())
        /*
        //tag.techList

        //sb.delete(sb.length - 2, sb.length)
        for (tech in tag.techList) {
            if (tech == MifareClassic::class.java.name) {
                sb.append('\n')
                var type = "Unknown"
                try {
                    val mifareTag = MifareClassic.get(tag)
                    when (mifareTag.type) {
                        MifareClassic.TYPE_CLASSIC -> type = "Classic"
                        MifareClassic.TYPE_PLUS -> type = "Plus"
                        MifareClassic.TYPE_PRO -> type = "Pro"
                    }
                    sb.append("Mifare Classic type: ")
                    sb.append(type)
                    sb.append('\n')
                    sb.append("Mifare size: ")
                    sb.append(mifareTag.size.toString() + " bytes")
                    sb.append('\n')
                    sb.append("Mifare sectors: ")
                    sb.append(mifareTag.sectorCount)
                    sb.append('\n')
                    sb.append("Mifare blocks: ")
                    sb.append(mifareTag.blockCount)
                } catch (e: Exception) {
                    sb.append("Mifare classic error: " + e.message)
                }
            }
            if (tech == MifareUltralight::class.java.name) {
                sb.append('\n')
                val mifareUlTag = MifareUltralight.get(tag)
                var type = "Unknown"
                when (mifareUlTag.type) {
                    MifareUltralight.TYPE_ULTRALIGHT -> type = "Ultralight"
                    MifareUltralight.TYPE_ULTRALIGHT_C -> type = "Ultralight C"
                }
                sb.append("Mifare Ultralight type: ")
                sb.append(type)
            }
        }
        return sb.toString()*/
    }

}