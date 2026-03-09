package net.unit8.jetshell.tool;

import java.io.ByteArrayInputStream;

class TestingInputStream extends ByteArrayInputStream {

    TestingInputStream() {
        super(new byte[0]);
    }

    void setInput(String s) {
        this.buf = s.getBytes();
        this.pos = 0;
        this.count = buf.length;
    }
}
