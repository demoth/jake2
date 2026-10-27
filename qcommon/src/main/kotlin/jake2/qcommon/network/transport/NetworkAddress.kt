package jake2.qcommon.network.transport

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID

/** Resolved IP value. Copies bytes at the boundary and retains IPv6 scope identity. */
class IpAddress private constructor(private val bytes: ByteArray, private val scopeId: Int) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    fun toInetAddress(): InetAddress = if (bytes.size == 16) {
        Inet6Address.getByAddress(null, bytes, scopeId)
    } else {
        InetAddress.getByAddress(bytes)
    }

    override fun equals(other: Any?): Boolean =
        other is IpAddress && scopeId == other.scopeId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + scopeId
    override fun toString(): String = toInetAddress().hostAddress

    companion object {
        @JvmStatic
        fun fromBytes(bytes: ByteArray): IpAddress {
            require(bytes.size == 4 || bytes.size == 16) { "Expected an IPv4 or IPv6 address" }
            return IpAddress(bytes.copyOf(), 0)
        }

        @JvmStatic
        fun fromInetAddress(address: InetAddress): IpAddress =
            IpAddress(address.address, (address as? Inet6Address)?.scopeId ?: 0)
    }
}

sealed interface NetworkAddress {
    data class Udp(val ip: IpAddress, val port: Int) : NetworkAddress {
        init {
            require(port in 0..65535) { "UDP port out of range: $port" }
        }

        fun toSocketAddress(): InetSocketAddress = InetSocketAddress(ip.toInetAddress(), port)

        companion object {
            @JvmStatic
            fun fromSocketAddress(address: InetSocketAddress): Udp {
                require(!address.isUnresolved) { "Resolve the hostname before opening an endpoint" }
                return Udp(IpAddress.fromInetAddress(address.address), address.port)
            }
        }
    }

    data class Local(val linkId: UUID, val side: LocalSide) : NetworkAddress
}

enum class LocalSide { FIRST, SECOND }
