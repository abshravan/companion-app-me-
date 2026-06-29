package com.relayme.protocol

import com.relayme.core.Hello
import com.relayme.core.Protocol
import com.relayme.core.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolCodecTest {

    @Test fun encodesHelloWithAllFields() {
        val line = ProtocolCodec.encodeHello(Hello(Role.ANDROID, 1, "Pixel 7"))
        assertEquals("""{"type":"hello","role":"android","v":1,"name":"Pixel 7"}""", line)
    }

    @Test fun decodesAndroidHello() {
        val inbound = ProtocolCodec.decode("""{"type":"hello","role":"android","v":1,"name":"Pixel 7"}""")
        val hello = assertIs<ProtocolCodec.Inbound.HelloReceived>(inbound).hello
        assertEquals(Role.ANDROID, hello.role)
        assertEquals(1, hello.version)
        assertEquals("Pixel 7", hello.name)
    }

    @Test fun helloRoundTrips() {
        val original = Hello(Role.JAVAME, Protocol.VERSION, "Nokia 6300")
        val decoded = ProtocolCodec.decode(ProtocolCodec.encodeHello(original))
        assertEquals(original, assertIs<ProtocolCodec.Inbound.HelloReceived>(decoded).hello)
    }

    @Test fun missingNameDefaultsToEmpty() {
        val inbound = ProtocolCodec.decode("""{"type":"hello","role":"javame","v":1}""")
        assertEquals("", assertIs<ProtocolCodec.Inbound.HelloReceived>(inbound).hello.name)
    }

    @Test fun missingVersionDefaultsToCurrent() {
        val inbound = ProtocolCodec.decode("""{"type":"hello","role":"android"}""")
        assertEquals(Protocol.VERSION, assertIs<ProtocolCodec.Inbound.HelloReceived>(inbound).hello.version)
    }

    @Test fun helloWithUnknownRoleIsRejected() {
        // Required field invalid -> drop message (spec §7 rule 4).
        assertNull(ProtocolCodec.decode("""{"type":"hello","role":"toaster"}"""))
    }

    @Test fun unknownTypeIsUnhandledNotFatal() {
        val inbound = ProtocolCodec.decode("""{"type":"battery","level":82}""")
        assertEquals("battery", assertIs<ProtocolCodec.Inbound.Unhandled>(inbound).type)
    }

    @Test fun commandIsRecognisedAsUnhandledInThisMilestone() {
        val inbound = ProtocolCodec.decode("""{"cmd":"music_next"}""")
        assertEquals("music_next", assertIs<ProtocolCodec.Inbound.Unhandled>(inbound).cmd)
    }

    @Test fun unknownExtraFieldsAreIgnored() {
        val inbound = ProtocolCodec.decode("""{"type":"hello","role":"android","future":"x","v":1}""")
        assertIs<ProtocolCodec.Inbound.HelloReceived>(inbound)
    }

    @Test fun malformedLineReturnsNull() {
        assertNull(ProtocolCodec.decode("""{"type":"hello",,,}"""))
        assertNull(ProtocolCodec.decode("not json"))
        assertNull(ProtocolCodec.decode(""))
        assertNull(ProtocolCodec.decode("""["array"]"""))
    }

    @Test fun escapedCharactersSurviveRoundTrip() {
        val tricky = Hello(Role.ANDROID, 1, "Line\"quote\\slash\ttab")
        val decoded = ProtocolCodec.decode(ProtocolCodec.encodeHello(tricky))
        assertEquals(tricky.name, assertIs<ProtocolCodec.Inbound.HelloReceived>(decoded).hello.name)
    }

    @Test fun encodedLineHasNoRawNewline() {
        val line = ProtocolCodec.encodeHello(Hello(Role.ANDROID, 1, "a\nb"))
        assertTrue('\n' !in line, "framing must not contain raw newlines")
    }
}
