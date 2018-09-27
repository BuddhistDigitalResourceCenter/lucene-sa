# Lucene Analyzers for Sanskrit

This repository contains bricks to implement a full analyzer pipeline in Lucene:

- filters to normalize and convert SLP1, Devanagari and IAST into SLP1
- indexation in SLP1 or simplified IAST with no diacritics (for lenient search)
- stopwords filter
- a syllable-based tokenizer
- a word tokenizer (that doesn't break compounds)
- a compound tokenizer with the following features:
    - maximal matching algorithm with desandhification
    - customizable word/compound list
    - filter to merge prepositions/preverbs to the following verb
    - basic Part-Of-Speech attribution to word tokens
    - user-defined word lists

## Installation through maven:

```xml
    <dependency>
      <groupId>io.bdrc.lucene</groupId>
      <artifactId>lucene-sa</artifactId>
      <version>0.1.0</version>
    </dependency>
```

## Components

### SanskritAnalyzer

#### Constructors

```
    SanskritAnalyzer(String mode, String inputEncoding)
```
 - `mode`: `space`(tokenize at spaces), `syl`(tokenize in syllables) or `word`(tokenize in words)
 - `inputEncoding`: `SLP`(SLP1 encoding), `deva`(devanagari script) or `roman`(IAST)
 

```
    SanskritAnalyzer(String mode, String inputEncoding, String stopFilename)
    
```
 - `stopFilename`: path to the file, empty string (default list) or `null` (no stopwords)

```
    SanskritAnalyzer(String mode, String inputEncoding, boolean mergePrepositions, boolean filterGeminates)
```
 - `mergePrepositions`: concatenates the token containing a preposition with the next one if true.
 - `filterGeminates`: simplify geminates(see below) if `true`, else keep them as-is (default behavior).  If the input text may contain geminates and the tokenization mode is `word`, make sure this is set to true to avoid stumbling on the spelling variations.
 
```
    SanskritAnalyzer(String mode, String inputEncoding, boolean mergePrepositions, boolean filterGeminates, String lenient)
```
 - `lenient`: `index` or `query` (requires this information to select the correct filter pipeline) 

In all configurations except when lenient is activated, the output tokens of the analyzers are always encoded in SLP1.
Lenient analyzers output a drastically simplified IAST (see below for details).

#### Usecases
Three usecases are given as examples of possible configurations

##### 1. Regular search
A text in IAST is tokenized in words for indexing. The queries are in SLP and tokenized in words. The default stopwords list is applied.
- Indexing:  `SanskritAnalyzer("word", "roman")`
- Querying:  `SanskritAnalyzer("word", "SLP")`

##### 2. Lenient search (words)
A text in IAST is tokenized in words. The queries in IAST are tokenized at spaces: search users provide separate words with no sandhi applied. Geminates are normalized(`true`) only at indexing time so that geminates are considered to be spelling variants instead of mistakes. The lenient search is enabled by indicating either "index" or "query", thereby selecting the appropriate pipeline of filters.
- Indexing:  `SanskritAnalyzer("word", "roman", true, false, "index")`
- Querying:  `SanskritAnalyzer("space", "roman", false, false, "query")`

##### 3. Lenient search (syllables)
The encoding of the text to index and that of the query is the same as above. Geminates are not normalized(yet could be) because the input text and the queries are tokenized in syllables. Lenient search is also enabled in the same way.
- Indexing:  `SanskritAnalyzer("syl", "roman", false, false, "index")`
- Querying:  `SanskritAnalyzer("syl", "roman", false, false, "query")`

### SkrtWordTokenizer

This tokenizer produces words through a Maximal Matching algorithm. It builds on top of [this Trie implementation](https://github.com/BuddhistDigitalResourceCenter/stemmer).

#### Dealing with Morphology
A sanskrit word typically undergoes two morphological processes before reaching its surface form(what is seen in free text): nominal or verbal inflection and sandhi.

##### 1. Inflection 
We rely on the resources found in Sanskrit Heritage (see [sanskrit-stemming-data](https://github.com/BuddhistDigitalResourceCenter/sanskrit-stemming-data/tree/master/SH_parse)) for that part. This provides us with inflected-form/lemma pairs that actually constitutes the better part of our lexical resources.

##### 2. Sandhi
Within `sanskrit-stemming-data`, every inflected-form/lemma pair then undergoes sandhi. 
Every new form generated by applying sandhi on a given inflected form together with its `cmd` containing additional information then becomes an entry in the Trie.
The `cmd` allows to later retrieve the lemmas from the surface form (multiple sandhis having differing lemmas can give the same surface form). For each sandhi that transforms the inflected form into the surface form of a given entry, the transformation of the beginning of the next word is also encoded.

The part-of-speech of each entry is also given. It is currently used in a TokenFilter to join preposition(s)/preverb(s) and the word following them.

#### Parsing
The sequences of valid sanskrit characters of the input string are fed to the Trie, generating word tokens and non-word tokens.
Once a match is found, the right lemma(s) are retrieved by:
 - constructing every sandhied context from the surface form(the current match) and every possible beginning of next word encoded in every sandhi found in the `cmd`.
 - each sandhied context is compared to what actually lies in the input between the current word and the next one.
 - each matching sandhied context then produces the correct lemmas(there can be more than one sandhi per context, thus more than one lemma) and each sandhi in turn will produce the potential initials of the next word.
 - when parsing the next word, one potential beginning(an `initial`) at a time replaces what is found in the input. All the matches found(including the expected one) are kept and will be returned by the Tokenizer.    

Additional sandhied contexts are generated to account for cases when the expected beginning is the input without any modifications. The beginnings the next word found by applying these "idempotent sandhis" are all the phonemes(SLP letters) that a given sandhi(vowel sandhi, visarga sandhi, etc.) doesn't modify.

#### Limitations and workarounds
##### Lexical resources
Being built on top of the lexical resources compiled in the Trie, the Tokenizer is limited to the vocabulary and inflected forms found in the [XML files](https://gitlab.inria.fr/huet/Heritage_Resources/tree/master/XML/SL) of Sanskrit Heritage Resources. Although these resources are the best available both in terms of size and coverage, they were not intended to be used as self-sufficient lexical resources. So, some words or forms that obviously ought to be there will be found missing. 

In order to fill in the gaps, `sanskrit-stemming-data`[`/input/custom_entries/`](https://github.com/BuddhistDigitalResourceCenter/sanskrit-stemming-data/tree/master/input/custom_entries) holds custom entries. Each custom entry is transformed into sandhied surface forms and the corresponding `cmd`s are generated.

##### Maximal Matching 
By arbitrarily choosing the end of the longest word as the starting point of the next word, we can't avoid [garden paths](https://en.wikipedia.org/wiki/Garden_path_sentence), yet within the context of Lucene Analyzers, we can't afford generating forests of possible parses to choose from for a given sentence.

As a workaround, entries with "multi-token lemmas" in the stead of `cmd`s can be added in `sanskrit-stemming-data`[`/input/maxmatch_workaround/`](https://github.com/BuddhistDigitalResourceCenter/sanskrit-stemming-data/tree/master/input/maxmatch_workaround). It will then be made into different tokens by the Tokenizer as specified in the multi-token lemma. 

Given a Trie containing the following.
- `caryā`, lemma: caryā
- `caryāva`, lemma: caryā
- `avatāra`, lemma: avatāra
Parsing `caryāvatāra` will result in `caryāva` + `tāra`(non-word token).

Adding the following workaround entry will solve this combination(and all sandhied variants).
 - `caryāvatāra`, lemmas: `caryā`, `avatāra` 
 
The Tokenizer now outputs what is expected.

#### Parsing sample from Siddham Project data

Courtesy of Dániel Balogh, sample data from Siddham with the tokens produced.
Legend: `√`: word token(lemmatized), `✓`: word token, `❌`: non-word token.

```
yaḥ kulyaiḥ svai … #ātasa … yasya … … puṃva … tra … … sphuradvaṃ … kṣaḥ sphuṭoddhvaṃsita … pravitata
¦ yad√ ¦ kulyā√ kulya√ ¦ sva√ sva√ ¦ at√ ¦ ya√ yad√ yas√ ¦ puṃs√ ¦ va✓ ¦ tra✓ ¦ sphurat√ ¦ va✓ ¦ ṃ❌ ¦ kṣa√ ¦ sphuṭ√ sphuṭa√ ¦ dhvaṃs√ ¦ ut√ ¦ pra√ ¦ vi√ ¦ tan√ 

 … yasya prajñānuṣaṅgocita-sukha-manasaḥ śāstra-tattvārttha-bharttuḥ … stabdho … hani … nocchṛ …
¦ ya√ yad√ yas√ ¦ prajña√ ¦ anu√ ¦ saj√ ¦ ta✓ ucita✓ cita✓ ¦ sukha√ ¦ manasā√ manas√ ¦ śāstṛ√ śāstra√ ¦ ad√ tattva√ ¦ ārtha√ artha√ ¦ bhartṛ√ ¦ stabdha√ ¦ u❌ ¦ han√ ¦ na√ ¦ ut√ 

 sat-kāvya-śrī-virodhān budha-guṇita-guṇājñāhatān eva kṛtvā vidval-loke ’vināśi sphuṭa-bahu
¦ sad√ sat√ ¦ kāvya√ ¦ śrī√ ¦ irā√ ¦ virodha√ ¦ dha✓ budha√ ¦ guṇita√ ¦ guṇa√ ¦ ājñā√ ā√ ¦ han√ ¦ eva√ ¦ kṛtvan√ ¦ va✓ ¦ vidvas√ ¦ lok√ loka√ ¦ avināśin√ ¦ sphuṭ√ sphuṭa√ ¦ bahu√ 

 -kavitā-kīrtti rājyaṃ bhunakti āryyaihīty upaguhya bhāva-piśunair utkarṇṇitai romabhiḥ sabhyeṣūcchvasiteṣu 
¦ kū√ ¦ a✓ ¦ kīrti√ ¦ rājya√ ¦ bhunakti√ ¦ āra√ ārya√ ¦ hi√ eha√ ¦ iti√ ¦ upagu√ ¦ hi√ ¦ av√ bhā√ bhā√ bhāva√ bhū√ bha√ bhu√ ¦ śuna√ śvan√ ¦ a✓ ¦ śuna√ śvan√ ¦ utkarṇita√ ¦ roman√ ¦ sabhya√ ¦ vasita√ ¦ ut√ 

tulya-kula-ja-mlānānanodvīkṣitaḥ sneha-vyāluḷitena bāṣpa-guruṇā tattvekṣiṇā cakṣuṣā yaḥ pitrābhihito nirīkṣya
¦ tulya✓ya✓ ¦ kula√ ¦ ja✓ ¦ mlāna√ ¦ an✓ ¦ ā√ ¦ vi√ ¦ ut√ ¦ dū√ dva√ ¦ īkṣita√ īkṣitṛ√ ¦ ij√ ¦ kṣi√ kṣita√ ¦ nah√ ¦ vi√ ¦ ā√ vi√ ¦ ālu√ ¦ al✓ ¦ lul√ lulita√ ¦ bāṣpa√ ¦ guru√ ¦ tattva√ ¦ īkṣ√ ¦ ij√ ¦ a✓ ¦ cakṣus√ ¦ yad√ ¦ pitṛ√ ¦ bhī√ abhi√ abhi√ ¦ dhā√ hita√ ¦ ni√ ¦ rā√ rai√ ¦ īkṣ√ īkṣa√ ¦ ij√ ¦ ya✓ 

nikhilāṃ pāhy evam urvvīm iti dṛṣṭvā karmmāṇy anekāny amanuja-sadṛśāny adbhutodbhinna-harṣā bhāvair 
¦ khila√ nikhila√ ¦ pā√ ¦ evam√ ¦ uru√ ¦ iti√ iti√ ¦ dṛṣ√ ¦ karman√ ¦ aneka√ ¦ amat√ ¦ uj✓ ¦ a✓ ¦ sadṛśa√ sadṛśa√ ¦ adbhuta√ ¦ bhid√ ¦ udbhid√ ¦ harṣa√ harṣa√ hṛṣ√ ¦ na√ a✓ ¦ bhā√ bha√ ṛṣ√ ¦ av√ bhā√ bhā√ bhāva√ bhāva√ bhū√ bha√ bhu√ 

āsvādayantaḥ … keciT vīryyottaptāś ca kecic charaṇam upagatā yasya 
¦ āsvādayat√ ¦ kim√ ¦ cid√ cit√ cit√ ¦ vīra√ vīrya√ vīrya√ ¦ tap√ ¦ utta✓ ¦ ca√ ¦ kim√ ¦ cid√ cit√ ¦ aṇa✓ ¦ śaraṇa√ ¦ upaga✓ ¦ tā✓ ¦ ya√ yad√ yas√ 

vṛtte praṇāme ’py artti
¦ vṛtta√ vṛtti√ ¦ īraṇa√ ¦ ira✓ ¦ praṇāma√ ¦ pā√ api√ ¦ arti√
```

### SkrtSyllableTokenizer

Produces syllable tokens using the same syllabation rules found in Peter Scharf's [script](http://www.sanskritlibrary.org/Sanskrit/SanskritTransliterate/syllabify.html). 

### Stopword Filter

The [list of stopwords](src/main/resources/skrt-stopwords.txt) is [this list](https://gist.github.com/Akhilesh28/b012159a10a642ed5c34e551db76f236) encoded in SLP
The list must be formatted in the following way:

 - in SLP encoding
 - 1 word per line
 - empty lines (with and without comments), spaces and tabs are allowed
 - comments start with `#`
 - lines can end with a comment

### GeminateNormalizingFilter

Geminates of consonants besides a "r" or "y" was a common practice in old publications. These non-standard spellings need to be normalized in order to be correctly tokenized in words.

This filter applies the following simplification rules:

```  
    CCr   →  Cr 
    rCC   →  rC
    CCy   →  Cy
```

`C` is any consonant in the following list: [k g c j ṭ ḍ ṇ t d n p b m y v l s ś ṣ]
The second consonant can be the aspirated counterpart(ex: `rtth`), in which case the consonant that is kept is the aspirated one.
Thus, "arttha" is normalized to "artha",  "dharmma" to "dharma".
 
### Roman2SlpFilter

Transcodes the romanized sanskrit input in SLP.

Following the naming convention used by Peter Scharf, we use "Roman" instead of "IAST" to show that, on top of supporting the full IAST character set, we support the extra distinctions within devanagari found in ISO 15919
In this filter, a list of non-Sanskrit and non-Devanagari characters are deleted.

See [here](src/main/java/io/bdrc/lucene/sa/Roman2SlpFilter.java) for the details.

### Slp2RomanFilter

Transcodes the SLP input in IAST.

Outputs fully composed forms(single Unicode codepoints) instead of relying on extra codepoints for diacritics.

### Deva2SlpFilter

Transcodes the devanagari sanskrit input in SLP.

This filter also normalizes non-Sanskrit Devanagari characters. Ex: क़ => क

### Lenient Search Mode
`SanskritAnalyzer` in lenient mode outputs tokens encoded in simplified sanskrit instead of SLP.
 
This following transformations are applied to the IAST transcription:
 - all long vowels become short
 - all aspirated consonants become unaspirated
 - "ṃ" and "ṁ" become "m"
 - "ṅ" becomes "n"
 - all remaining diacritics are removed

Keeping in the same spirit, these informal conventions are modified: 
 - "sh"(for "ś") becomes "s"
 - "ri"(for "ṛ") becomes "r"
 - "li"(for "ḷ") becomes "l"
 - "v" becomes "b" (arbitrarily)

In terms of implementation, the input normalization happening in `Roman2SlpFilter` and `Deva2SlpFilter` is leveraged by always applying them first, then transforming SLP into lenient sanskrit.  
Relying on Roman2SlpFilter has the additional benefit of correctly dealing with capital letters by lower-casing the input.

#### LenientCharFilter
Used at query time.

Expects SLP as input.
Applies the modifications listed above.

#### LenientTokenFilter
Used at index time.

Expects IAST as input. (`Slp2RomanFilter` can be used to achieve that)
Applies the modifications listed above. 

## Resources

SkrtWordTokenizer uses the data generated [here](https://github.com/BuddhistDigitalResourceCenter/sanskrit-stemming-data) as its lexical resources.

## Building from source

### Build the lexical resources for the Trie:

These steps need only be done once for a fresh clone of the repo; or simply run the `initialize.sh` script

 - make sure the submodules are initialized (`git submodule init`, then `git submodule update`), first from the root of the repo, then from `resources/sanskrit-stemming-data`
 - build lexical resources for the main trie: `cd resources/sanskrit-stemming-data/sandhify/ && python3 sandhifier.py`
 - build sandhi test tries: `cd resources/sanskrit-stemming-data/sandhify/ && python3 generate_test_tries.py`
     if you encounter a `ModuleNotFoundError: No module named 'click'` you may need to `python3 -m pip install click`
 - update other test tries with lexical resources: `cd src/test/resources/tries && python3 update_tries.py`
 - compile the main trie: `mvn exec:java -Dexec.mainClass="io.bdrc.lucene.sa.BuildCompiledTrie"` 
       (takes about 45mn on an average laptop). **This is not actually needed since it is done in the `mvn compile` (see below)**

The base command line to build a jar is:

```
mvn clean compile exec:java package
```

The following options modify the package step:

- `-DincludeDeps=true` includes `io.bdrc.lucene:stemmer` in the produced jar file
- `-DperformRelease=true` signs the jar file with gpg

## Building and using SanskritAnalyzer with eXist db

For the sake of 84000.co SanskritAnalyzer was (back)ported to work with Lucene 4.10.4 and eXist db 3.4.1
Following are the steps to build the appropriate jar containing SanskritAnalyzer and plugging it in eXist db:
1. Follow the steps specified in section **Building from source** up to (but not including) the mvn build step
2. Switch to the correct branch: **git checkout lucene4_port**
3. Then do the mvn build step: **mvn clean compile exec:java package**
4. Copy the newly built jar file to the appropriate eXist directory, in my case:
   **cp $(PRJ_DIR)/target/lucene-sa-0.2.0.jar $(EXIST_DIR)/lib/user/**
5. If there is an old jar (for SanskritAnalyzer) with a different name in $(EXIST_DIR)/lib/user -- get rid of it!
6. Start (or Restart) eXist
7. Finally, edit the relevant collection.xconf file(s) to properly refer to the SanskritAnalyzer,
   providing the required _mode_ and _inputEncoding_ parameters:

        <lucene diacritics="no">
            <analyzer class="io.bdrc.lucene.sa.SanskritAnalyzer">
            	<param name="mode" type="java.lang.String" value="word"/> <!-- may be "space", "syl" or "word" -->
            	<param name="inputEncoding" type="java.lang.String" value="roman"/> <!-- may be "SLP", "deva" or "roman" -->
            </analyzer>
            ...
8. Voilà!

## Aknowledgements

 - https://gist.github.com/Akhilesh28/b012159a10a642ed5c34e551db76f236
 - http://sanskritlibrary.org/software/transcodeFile.zip (more specifically roman_slp1.xml)
 - https://en.wikipedia.org/wiki/ISO_15919#Comparison_with_UNRSGN_and_IAST
 - http://unicode.org/charts/PDF/U0900.pdf

## License

The code is Copyright 2017, 2018 Buddhist Digital Resource Center, and is provided under [Apache License 2.0](LICENSE).
