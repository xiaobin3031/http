import sun.misc.BASE64Decoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Test {

    public static void main(String[] args) throws IOException {
        Test test = new Test();
        test.testT();
    }

    private void testT() throws IOException {
        BASE64Decoder base64Decoder = new BASE64Decoder();
        System.out.println(new String(base64Decoder.decodeBuffer("QvePN5PzGtSmt6thojO77oYHPk/9R34W82/3MQ=="), StandardCharsets.UTF_16LE));
    }
}
