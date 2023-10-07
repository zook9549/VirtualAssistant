package ai.asktheexpert.virtualassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VirtualAssistantApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private FileStore fileStore;

    @Test
    public void testSaveFile() throws Exception {
        // given
        String name = "test.mp4";
        byte[] content = "adfad3".getBytes();

        // when
        String path = fileStore.save(name, content);
        System.out.println(path);

    }

    @Test
    public void testDeleteile() throws Exception {
        // given
        String name = "test.mp4";
        // when
        boolean result = fileStore.delete(name);

    }

    @Test
    public void testGetFile() throws Exception {
        // given
        String name = "test.mp4";
        // when
        byte[] result = fileStore.get(name);
        System.out.println(new String(result));

    }

}
