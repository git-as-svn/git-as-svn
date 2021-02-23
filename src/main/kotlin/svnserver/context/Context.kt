/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.annotation.concurrent.ThreadSafe

/**
 * Base context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
abstract class Context<S : AutoCloseable> : AutoCloseable {
    private val map: ConcurrentHashMap<Class<out S>, S> = ConcurrentHashMap()
    protected fun values(): Collection<S> {
        return map.values
    }

    fun <T : S> add(type: Class<T>, `object`: T): T {
        if (map.put(type, `object`) != null) {
            throw IllegalStateException("Object with type " + type.name + " is already exists in shared context.")
        }
        return `object`
    }

    operator fun <T : S?> get(type: Class<T>): T? {
        return map[type] as T?
    }

    fun <T : S?> remove(type: Class<T>): T? {
        return map.remove(type) as T?
    }

    fun <T : S> sure(type: Class<T>): T {
        return get(type) ?: throw IllegalStateException("Can't find object with type " + type.name + " in context")
    }

    fun <T : S> getOrCreate(type: Class<T>, supplier: Supplier<T>): T {
        val result: T? = get(type)
        if (result == null) {
            val newObj: T = supplier.get()
            val oldObj: T? = map.putIfAbsent(type, newObj) as T?
            if (oldObj != null) {
                return oldObj
            }
            return newObj
        }
        return result
    }

    @Throws(Exception::class)
    override fun close() {
        val values: List<S> = ArrayList(values())
        for (i in values.indices.reversed()) values[i].close()
    }
}
