package my.project.manabe_mk1.fwdnotice

object Log
{
    fun v(name: String?, value: String?) {
        android.util.Log.v(getRelative(getCalledBy()), "%s[%s]".format(name ?: "null", value ?: "null"))
    }

    fun d(name: String?, value: String?) {
        android.util.Log.d(getRelative(getCalledBy()), "%s[%s]".format(name ?: "null", value ?: "null"))
    }

    fun e(name: String?, value: String?) {
        android.util.Log.d(getRelative(getCalledBy()), "%s[%s]".format(name ?: "null", value ?: "null"))
    }

    /**
     * 完全修飾クラス名からパッケージ名を除いた部分を取り出す
     */
    private fun getRelative(fqcn: String): String {
        var splitFQCN = fqcn.split('.')
        var splitResult = listOf<String>()
        for(word in this.javaClass.name.split('.')) {
            if(!word.equals(splitFQCN.elementAt(0))) {
                return splitFQCN.reduce { left, right -> "$left.$right" }
            }
            splitFQCN = splitFQCN.drop(1)
        }
        return splitFQCN.reduce { left, right -> "$left.$right" }
    }

    /**
     * ログ出力を呼び出したクラスを特定する
     */
    private fun getCalledBy(): String {
        val myName = this.javaClass.name
        var prevName = "";
        for(stack in Thread.currentThread().stackTrace) {
            if(!"".equals(prevName) && !myName.equals(stack.className)) {
                return stack.className
            }
            if(myName.equals(stack.className)) prevName = stack.className
        }
        return "UnknownClass"
    }
}
