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

package cc.twittertools.index;

import java.io.Reader;

import com.google.common.base.CharMatcher;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.util.Version;

/**
 * {@link Analyzer} for Tweets.
 */
public final class TweetAnalyzer extends StopwordAnalyzerBase {
  private boolean stemming = true;

  private final CharArraySet stemExclusionSet;

  /**
   * Returns an unmodifiable instance of the default stop words set.
   * @return default stop words set.
   */
  public static CharArraySet getDefaultStopSet() {
    return DefaultSetHolder.DEFAULT_STOP_SET;
  }

  /**
   * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class
   * accesses the static final set the first time.;
   */
  private static class DefaultSetHolder {
    static final CharArraySet DEFAULT_STOP_SET = StandardAnalyzer.STOP_WORDS_SET;
  }

  /**
   * Builds an analyzer with the default stop words: {@link #getDefaultStopSet}.
   */
  public TweetAnalyzer() {
    this(DefaultSetHolder.DEFAULT_STOP_SET);
  }

  /**
   * @deprecated Use {@link #TweetAnalyzer()}
   */
  @Deprecated
  public TweetAnalyzer(Version matchVersion) {
    this(matchVersion, DefaultSetHolder.DEFAULT_STOP_SET);
  }

  /**
   * Builds an analyzer with the given stop words.
   *
   * @param stopwords a stopword set
   */
  public TweetAnalyzer(CharArraySet stopwords) {
    this(stopwords, CharArraySet.EMPTY_SET);
  }

  /**
   * @deprecated Use {@link #TweetAnalyzer(CharArraySet)}
   */
  @Deprecated
  public TweetAnalyzer(Version matchVersion, CharArraySet stopwords) {
    this(matchVersion, stopwords, CharArraySet.EMPTY_SET);
  }

  /**
   * Builds an analyzer with the default stop words: {@link #getDefaultStopSet}.
   *
   * @param stemming to optionally disable stemming
   */
  public TweetAnalyzer(boolean stemming) {
    this(DefaultSetHolder.DEFAULT_STOP_SET);
    this.stemming = stemming;
  }

  /**
   * @deprecated Use {@link #TweetAnalyzer(boolean)}
   */
  @Deprecated
  public TweetAnalyzer(Version matchVersion, boolean stemming) {
    this(matchVersion, DefaultSetHolder.DEFAULT_STOP_SET);
    this.stemming = stemming;
  }

  /**
   * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is
   * provided this analyzer will add a {@link SetKeywordMarkerFilter} before
   * stemming.
   *
   * @param stopwords        a stopword set
   * @param stemExclusionSet a set of terms not to be stemmed
   */
  public TweetAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
    super(stopwords);
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
  }

  /**
   * @deprecated Use {@link #TweetAnalyzer(CharArraySet, CharArraySet)}
   */
  @Deprecated
  public TweetAnalyzer(Version matchVersion, CharArraySet stopwords, CharArraySet stemExclusionSet) {
    super(matchVersion, stopwords);
    this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(
            matchVersion, stemExclusionSet));
  }

  /**
   * Creates a
   * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   * which tokenizes all the text in the provided {@link Reader}.
   *
   * @return A
   *         {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
   *         built from an {@link CharTokenizer} filtered with
   *         {@link StandardFilter}, {@link EnglishPossessiveFilter},
   *         {@link LowerCaseEntityPreservingFilter}, {@link StopFilter}
   *         , {@link SetKeywordMarkerFilter} if a stem exclusion set is
   *         provided and {@link PorterStemFilter}.
   */
  @Override
  protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
    Tokenizer source = new CharTokenizer(getVersion(), reader) {
      @Override
      protected boolean isTokenChar(int c) {
        return !CharMatcher.WHITESPACE.matches((char) c);
      }
    };
    TokenStream result = new StandardFilter(getVersion(), source);
    // prior to this we get the classic behavior, standardfilter does it for us.
    if (getVersion().onOrAfter(Version.LUCENE_3_1))
      result = new EnglishPossessiveFilter(getVersion(), source);
    result = new LowerCaseEntityPreservingFilter(result);
    // FIXME: LowerCaseFilter use blocked by LUCENE-3236
    result = new StopFilter(result, stopwords);
    if (stemming) {
      if (!stemExclusionSet.isEmpty())
        result = new SetKeywordMarkerFilter(result, stemExclusionSet);
      // Porter stemmer ignores words which are marked as keywords
      result = new PorterStemFilter(result);
    }
    return new TokenStreamComponents(source, result);
  }
}