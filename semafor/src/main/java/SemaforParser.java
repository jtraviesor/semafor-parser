import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.cmu.cs.lti.ark.fn.parsing.ParserDriver;
import edu.cmu.cs.lti.ark.preprocess.PreprocessedText;
import stanfordparser.StanfordParser;

/**
 * Created by ramini on 1/18/16.
 */

public class SemaforParser {
    private final String DECODING_TYPE= "ad3";

    SemaforConfig config;
    ParserDriver parserDriver;
    StanfordParser stanfordParser;

    public SemaforParser() throws IOException {
        config = SemaforConfig.getInstance();
        stanfordParser = StanfordParser.getInstance();
        initParser();
    }

    public void initParser() throws IOException {
        final String MODEL_DIR = config.getSemaforResource("models").getPath();
        final String GRAPH_FILE = MODEL_DIR + "/sparsegraph.gz";
        final String STOPWORDS =  config.getSemaforResource("stopwords.txt").getPath();
        final String WORDNET_CONFIG_FILE = config.getSemaforResource("file_properties.xml").getPath();
        String[] FNArgs;
        FNArgs = new String[]{
                "stopwords-file:"+STOPWORDS,
                "wordnet-configfile:"+ WORDNET_CONFIG_FILE,
                "fnidreqdatafile:" + MODEL_DIR + "/reqData.jobj",
                "goldsegfile:null",
                "userelaxed:no",
                "idmodelfile:" + MODEL_DIR + "/idmodel.dat",
                "framenet-femapfile:" + MODEL_DIR + "/framenet.frame.element.map",
                "model:" + MODEL_DIR + "/argmodel.dat",
                "useGraph:" + GRAPH_FILE,
                "requiresmap:" + MODEL_DIR + "/requires.map",
                "excludesmap:" + MODEL_DIR + "/excludes.map",
                "decoding:" + DECODING_TYPE
        };
        parserDriver = new ParserDriver(FNArgs);
    }

    /**
     * Given an input string, returns a list of FrameNet frames for that input.
     * @param text The given input string.
     * @return The list of FamerNet frames for the given input string.
     */
    public List<String> findFrames(String text){
        // Return an empty list if the input us null or empty
        if (text == null || text.trim().isEmpty()){
            return Lists.newArrayList();
        }

        List<String> frames = new ArrayList<String>();
        // Run the Semafor parser and get the frame features
        Set<String> frameFeatures = parserDriver.runParser(runPreprocessor(text));
        // Get the frame names
        for (String ff: frameFeatures) {
            frames.add(ff);
        }

        return frames;
    }

    /**
     * Preprocesses the given text and prepare it for frame identification.
     * @param text The given text.
     * @return The preprocessed text.
     */
    private List<PreprocessedText> runPreprocessor(String text) {
        return stanfordParser.parse(text);
    }

    public static void main(String[] args) throws IOException {
        SemaforParser semafor = new SemaforParser();

        System.out.print("\n Frame: ");
        System.out.println(semafor.findFrames("I need help with my invoice."));
        System.out.print("\n Frame: ");
        System.out.println(semafor.findFrames("How do you do?"));
    }
}
