package work.lclpnet.ap2.impl.data

class MutableDataManager : DataManager {
    private var data: DynamicData? = null

    override fun string(str: String): String {
        val data = this.data

        // check if str is a reference
        if (data != null && str.isNotEmpty() && str[0] == '@') {
            val key = str.substring(1)

            val dataStr = data[key]

            if (dataStr is String) {
                return dataStr
            }
        }

        return str
    }

    fun setData(data: DynamicData) {
        this.data = data
    }
}
