/**
 * Twitter Tools
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.twittertools.search.local;

import java.io.File;
import java.io.PrintStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import cc.twittertools.index.IndexStatuses;
import cc.twittertools.index.IndexStatuses.StatusField;
import cc.twittertools.search.TrecTopic;
import cc.twittertools.search.TrecTopicSet;

public class RunQueriesConjunctive {
  private static final String DEFAULT_RUNTAG = "lucene4lm";

  private static final String INDEX_OPTION = "index";
  private static final String QUERIES_OPTION = "queries";
  private static final String NUM_RESULTS_OPTION = "num_results";
  private static final String SIMILARITY_OPTION = "similarity";
  private static final String RUNTAG_OPTION = "runtag";
  private static final String VERBOSE_OPTION = "verbose";

  private RunQueriesConjunctive() {}

  @SuppressWarnings("static-access")
  public static void main(String[] args) throws Exception {
    Options options = new Options();

    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("index location").create(INDEX_OPTION));
    options.addOption(OptionBuilder.withArgName("num").hasArg()
        .withDescription("number of results to return").create(NUM_RESULTS_OPTION));
    options.addOption(OptionBuilder.withArgName("file").hasArg()
        .withDescription("file containing topics in TREC format").create(QUERIES_OPTION));
    options.addOption(OptionBuilder.withArgName("similarity").hasArg()
        .withDescription("similarity to use (BM25, LM)").create(SIMILARITY_OPTION));
    options.addOption(OptionBuilder.withArgName("string").hasArg()
        .withDescription("runtag").create(RUNTAG_OPTION));
    options.addOption(new Option(VERBOSE_OPTION, "print out complete document"));

    CommandLine cmdline = null;
    CommandLineParser parser = new GnuParser();
    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      System.exit(-1);
    }

    if (!cmdline.hasOption(QUERIES_OPTION) || !cmdline.hasOption(INDEX_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(RunQueries.class.getName(), options);
      System.exit(-1);
    }

    File indexLocation = new File(cmdline.getOptionValue(INDEX_OPTION));
    if (!indexLocation.exists()) {
      System.err.println("Error: " + indexLocation + " does not exist!");
      System.exit(-1);
    }

    String topicsFile = cmdline.getOptionValue(QUERIES_OPTION);
    
    int numResults = 20;
    try {
      if (cmdline.hasOption(NUM_RESULTS_OPTION)) {
        numResults = Integer.parseInt(cmdline.getOptionValue(NUM_RESULTS_OPTION));
      }
    } catch (NumberFormatException e) {
      System.err.println("Invalid " + NUM_RESULTS_OPTION + ": " + cmdline.getOptionValue(NUM_RESULTS_OPTION));
      System.exit(-1);
    }

    String similarity = "LM";
    if (cmdline.hasOption(SIMILARITY_OPTION)) {
      similarity = cmdline.getOptionValue(SIMILARITY_OPTION);
    }

    PrintStream out = new PrintStream(System.out, true, "UTF-8");

    IndexReader reader = DirectoryReader.open(FSDirectory.open(indexLocation));
    IndexSearcher searcher = new IndexSearcher(reader);

    if (similarity.equalsIgnoreCase("BM25")) {
      searcher.setSimilarity(new BM25Similarity());
    } else if (similarity.equalsIgnoreCase("LM")) {
      searcher.setSimilarity(new LMDirichletSimilarity(2500.0f));
    }

    QueryParser p = new QueryParser(Version.LUCENE_43, StatusField.TEXT.name,
        IndexStatuses.ANALYZER);

    long s = System.currentTimeMillis();

    int cnt = 0;
    TrecTopicSet topics = TrecTopicSet.fromFile(new File(topicsFile));
    for ( TrecTopic topic : topics ) {
      cnt++;
      Query query = p.parse("+" + topic.getQuery());

      long startTime = System.nanoTime();
      TopDocs rs = searcher.search(query, numResults);
      long dur = System.nanoTime() - startTime;
      out.println(topic.getQuery() + ": " + ( (float) dur/1e6) + "ms");
      
//      int i = 1;
//      for (ScoreDoc scoreDoc : rs.scoreDocs) {
//        Document hit = searcher.doc(scoreDoc.doc);
//        out.println(String.format("%s Q0 %s %d %f %s", topic.getId(),
//            hit.getField(StatusField.ID.name).numericValue(), i, scoreDoc.score, runtag));
//        if ( verbose) {
//          out.println("# " + hit.toString().replaceAll("[\\n\\r]+", " "));
//        }
//        i++;
//      }
    }

    out.println(cnt + " topics, total time: " + (System.currentTimeMillis() - s) + "ms");

    reader.close();
    out.close();
  }
}
