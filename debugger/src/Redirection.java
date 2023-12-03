import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

public class Redirection extends Thread {
    Reader in;
    Writer out;

    Redirection(InputStream is, OutputStream os) {
        super();
        in = new InputStreamReader(is);
        out = new OutputStreamWriter(os);
    }

    public void run() {
        char[] buf = new char[1024];
        int n = 0;
        try {
            while ((n = in.read(buf, 0, 1024)) >= 0)
                out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}