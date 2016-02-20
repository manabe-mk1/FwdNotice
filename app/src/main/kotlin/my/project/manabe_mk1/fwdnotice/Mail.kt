package my.project.manabe_mk1.fwdnotice

import javax.mail.Session
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication

import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

import java.util.*

import android.os.AsyncTask
import android.content.Context
import android.content.Intent

/**
 * https://code.google.com/archive/p/javamail-android/downloads
 * [Tool][Android][Sync Project with Gradle Files]
 */
class Mail(context: Context, session: Session, from: InternetAddress, username: String, password: String) {

    val context = context

    val session = session
    val addressFrom = from
    val username = username
    val password = password
    val encode = "utf-8"

    companion object {
        /**
         * StrictModeを無効化することでエラーを回避する
         */
        fun avoidStrictMode() {
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().permitAll().build())
        }

        /**
         * Gmail 送信用インスタンス
         * Gmailのセキュリティ制限を回避する
         *     A Gmailのセキュリティ制限を下げる
         *     B Google開発者用アカウントからアプリ用のパスワードを作成して使用する
         */
        fun getGmail(context: Context): Mail {
            val properties = Properties()
            properties.put("mail.transport.protocol",       "smtp")
            properties.put("mail.host",                     "smtp.gmail.com")
            properties.put("mail.smtp.host",                "smtp.gmail.com")
            properties.put("mail.smtp.port",                "587")
            properties.put("mail.smtp.socketFactory.port",  "465")
            properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            properties.put("mail.smtp.socketFactory.fallback", "false")
            properties.put("mail.smtp.auth", true)
            properties.put("mail.smtp.starttls.enable", "true")
            properties.put("mail.smtp.debug", "false")

            val username = context.getString(R.string.gmail_user)
            val password = context.getString(R.string.gmail_pass)
            return Mail(context, Session.getInstance(properties, object: Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        return PasswordAuthentication(username, password)
                    }
                }),
                InternetAddress(context.getString(R.string.gmail_from)),
                username, password)
        }

        /**
         * さくらインターネット送信用インスタンス
         */
        fun getSakuraMail(context: Context): Mail {
            val properties = Properties()
            properties.put("mail.transport.protocol",       "smtp")
            properties.put("mail.smtp.host",                context.getString(R.string.sakura_smtp))
            properties.put("mail.smtp.port",                "587")
            //properties.put("mail.smtp.socketFactory.port",  "587")
            //properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            //properties.put("mail.smtp.socketFactory.fallback", "false")
            properties.put("mail.smtp.auth", true)
            properties.put("mail.smtp.starttls.enable", "true")
            //properties.put("mail.smtp.ssl.trust", "*")
            //properties.put("mail.smtp.debug", "false")

            val username = context.getString(R.string.sakura_user)
            val password = context.getString(R.string.sakura_pass)
            return Mail(context, Session.getInstance(properties, object: Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        return PasswordAuthentication(username, password)
                    }
                }),
                InternetAddress(context.getString(R.string.sakura_from)),
                username, password)
        }
    }

    fun send(to: String, subject: String, body: String) {
        val message = MimeMessage(session)
        message.setSubject(subject, encode)
        message.setFrom(addressFrom)
        message.setRecipient(Message.RecipientType.TO, InternetAddress(to))

        val text = MimeBodyPart()
        text.setText(body, encode)
        val bodies = MimeMultipart()
        bodies.addBodyPart(text)
        message.setContent(bodies)

        val transport = session.getTransport()
        transport.connect(username, password)
        transport.sendMessage(message, message.allRecipients)
        transport.close()
    }

    fun sendAsync(to: String, subject: String, body: String, intent: Intent?) {
        val task = SendTask(to, subject, body, context, intent)
        task.execute(this)
    }

    class SendTask(
            to: String, subject: String, body: String,
            context: Context, intent: Intent?): AsyncTask<Mail, Int, String>() {
        val to = to
        val subject = subject
        val body = body

        val context = context
        val intent = intent

        override fun doInBackground(vararg mails: Mail?): String? {
            for(mail in mails) {
                mail ?: continue
                try {
                    mail.send(to, subject, body)
                } catch(e: javax.mail.AuthenticationFailedException) {
                    return "MailAuthenticationFailed"
                } catch (e: Exception) {
                    Log.e("Mail ${e.javaClass.name}", e.message)
                    throw e
                }
            }
            return "Send complete."
        }

        override fun onPostExecute(result: String?) {
            Log.d("Send mail async", result)

            if(intent != null) {
                intent.putExtra("SendResult", result ?: "null")
                context.sendBroadcast(intent)
            }
            super.onPostExecute(result)
        }
    }
}