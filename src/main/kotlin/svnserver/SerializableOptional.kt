package svnserver

import java.io.Serializable

class SerializableOptional<T>(val value: T?) : Serializable {
}
