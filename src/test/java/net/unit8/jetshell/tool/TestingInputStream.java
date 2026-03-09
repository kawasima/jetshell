package net.unit8.jetshell.tool;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class TestingInputStream extends ByteArrayInputStream {

    TestingInputStream() {
        super(new byte[0]);
    }

    void setInput(String s) {
        this.buf = s.getBytes(StandardCharsets.UTF_8);
        this.pos = 0;
        this.count = buf.length;
    }
}
