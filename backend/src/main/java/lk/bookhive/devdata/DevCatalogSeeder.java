package lk.bookhive.devdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.domain.LibrarySection;
import lk.bookhive.catalog.service.BookListService;
import lk.bookhive.catalog.service.BookListService.BookSort;
import lk.bookhive.catalog.service.BookListService.Filters;
import lk.bookhive.catalog.service.CatalogImportService;
import lk.bookhive.catalog.service.CatalogImportService.ImportReport;
import lk.bookhive.catalog.service.CategoryService;
import lk.bookhive.catalog.service.CategoryService.Form;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 200 real-looking books for local development (checklist step 186): about 40 well-known titles in
 * Sinhala, Tamil and English, plus generated ones in all three languages, with copies on named
 * shelves. Goes through the real CSV importer and category service, so the data is exactly what
 * staff could have entered.
 *
 * <p>ISBNs are invented (Sri Lankan 978-955 prefix) but have valid check digits. Runs only when the
 * catalog is empty, and always produces the same books (fixed random seed).
 */
@Component
class DevCatalogSeeder {

  private static final Logger log = LoggerFactory.getLogger(DevCatalogSeeder.class);
  private static final int TOTAL_BOOKS = 200;

  private final BookListService bookList;
  private final CategoryService categories;
  private final CatalogImportService importer;
  // Same books on every fresh database
  private final Random random = new Random(42);

  DevCatalogSeeder(
      BookListService bookList, CategoryService categories, CatalogImportService importer) {
    this.bookList = bookList;
    this.categories = categories;
    this.importer = importer;
  }

  /** One sample book. {@code authors} uses "; " between names, like the CSV. */
  private record Sample(
      String title,
      String subtitle,
      String authors,
      String cutter,
      String language,
      Integer year,
      String ddc,
      String publisher,
      int pages,
      boolean reference) {}

  void seed() {
    if (bookList.list(new Filters(null, null, null, null), BookSort.TITLE, 0, 1).getTotalElements()
        > 0) {
      log.info("Dev catalog: books already there, nothing to seed");
      return;
    }
    seedCategories();
    List<Sample> samples = new ArrayList<>(famousBooks());
    while (samples.size() < TOTAL_BOOKS) {
      samples.add(generated(samples.size()));
    }
    try {
      ImportReport report =
          importer.importCsv(
              new ByteArrayInputStream(csv(samples).getBytes(StandardCharsets.UTF_8)), null, false);
      log.info(
          "Dev catalog: {} books, {} copies ({} rows failed)",
          report.created(),
          report.copiesAdded(),
          report.failed());
      report.results().stream()
          .filter(row -> row.error() != null)
          .limit(5)
          .forEach(row -> log.warn("Dev catalog row {}: {}", row.line(), row.error()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // --- Categories: a small Dewey tree, names in en / si / ta --------------------------------

  private record Cat(
      String ddc, String parent, String en, String si, String ta, LibrarySection section) {}

  private static final List<Cat> CATEGORIES =
      List.of(
          cat("000", null, "General works", "සාමාන්‍ය කෘති", "பொது நூல்கள்"),
          new Cat(
              "030",
              "000",
              "Encyclopedias",
              "විශ්වකෝෂ",
              "கலைக்களஞ்சியங்கள்",
              LibrarySection.REFERENCE),
          cat("005", "000", "Computer programming", "පරිගණක ක්‍රමලේඛනය", "கணினி நிரலாக்கம்"),
          cat(
              "100",
              null,
              "Philosophy & psychology",
              "දර්ශනය සහ මනෝවිද්‍යාව",
              "தத்துவமும் உளவியலும்"),
          cat("150", "100", "Psychology", "මනෝවිද්‍යාව", "உளவியல்"),
          cat("200", null, "Religion", "ආගම", "சமயம்"),
          cat("294.3", "200", "Buddhism", "බුද්ධාගම", "பௌத்தம்"),
          cat("294.5", "200", "Hinduism", "හින්දු ආගම", "இந்து சமயம்"),
          cat("300", null, "Social sciences", "සමාජ විද්‍යා", "சமூக அறிவியல்"),
          cat("330", "300", "Economics", "ආර්ථික විද්‍යාව", "பொருளியல்"),
          cat("400", null, "Language", "භාෂාව", "மொழி"),
          new Cat(
              "423",
              "400",
              "English dictionaries",
              "ඉංග්‍රීසි ශබ්දකෝෂ",
              "ஆங்கில அகராதிகள்",
              LibrarySection.REFERENCE),
          cat("500", null, "Science", "විද්‍යාව", "அறிவியல்"),
          cat("510", "500", "Mathematics", "ගණිතය", "கணிதம்"),
          cat("520", "500", "Astronomy", "තාරකා විද්‍යාව", "வானியல்"),
          cat(
              "576",
              "500",
              "Evolution & genetics",
              "පරිණාමය සහ ප්‍රවේණි විද්‍යාව",
              "பரிணாமமும் மரபியலும்"),
          cat("600", null, "Technology", "තාක්ෂණය", "தொழில்நுட்பம்"),
          cat("630", "600", "Agriculture", "කෘෂිකර්මය", "விவசாயம்"),
          cat("641.5", "600", "Cooking", "ආහාර පිසීම", "சமையல்"),
          cat("700", null, "Arts & recreation", "කලා සහ විනෝදාස්වාදය", "கலைகளும் பொழுதுபோக்கும்"),
          cat("793", "700", "Dance", "නර්තනය", "நடனம்"),
          cat("796", "700", "Sports", "ක්‍රීඩා", "விளையாட்டு"),
          cat("800", null, "Literature", "සාහිත්‍යය", "இலக்கியம்"),
          cat("823", "800", "English fiction", "ඉංග්‍රීසි ප්‍රබන්ධ", "ஆங்கிலப் புனைகதை"),
          cat("891.48", "800", "Sinhala literature", "සිංහල සාහිත්‍යය", "சிங்கள இலக்கியம்"),
          cat("894.811", "800", "Tamil literature", "දෙමළ සාහිත්‍යය", "தமிழ் இலக்கியம்"),
          new Cat(
              "808.068",
              "800",
              "Children's books",
              "ළමා පොත්",
              "சிறுவர் நூல்கள்",
              LibrarySection.CHILDREN),
          new Cat(
              "808.83",
              "800",
              "Young adult fiction",
              "යෞවන ප්‍රබන්ධ",
              "இளையோர் புனைகதை",
              LibrarySection.YOUNG_ADULT),
          cat(
              "900",
              null,
              "History & geography",
              "ඉතිහාසය සහ භූගෝල විද්‍යාව",
              "வரலாறும் புவியியலும்"),
          cat("910", "900", "Geography & travel", "භූගෝලය සහ සංචාරය", "புவியியலும் பயணமும்"),
          cat("954.93", "900", "History of Sri Lanka", "ශ්‍රී ලංකා ඉතිහාසය", "இலங்கை வரலாறு"));

  private static Cat cat(String ddc, String parent, String en, String si, String ta) {
    return new Cat(ddc, parent, en, si, ta, LibrarySection.ADULT);
  }

  private void seedCategories() {
    if (!categories.tree().isEmpty()) {
      return;
    }
    // Parents come before their children in the list, so their ids are known
    Map<String, UUID> ids = new HashMap<>();
    for (Cat c : CATEGORIES) {
      Category created =
          categories.create(
              new Form(
                  c.parent() == null ? null : ids.get(c.parent()),
                  c.en(),
                  c.si(),
                  c.ta(),
                  c.ddc(),
                  c.section()),
              null);
      ids.put(c.ddc(), created.getId());
    }
    log.info("Dev catalog: {} categories", CATEGORIES.size());
  }

  // --- Well-known books -----------------------------------------------------------------------

  private static List<Sample> famousBooks() {
    return List.of(
        // Sinhala
        si("ගම්පෙරළිය", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1944, "891.48", "Sarasavi Publishers", 296),
        si("යුගාන්තය", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1949, "891.48", "Sarasavi Publishers", 248),
        si("කලියුගය", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1957, "891.48", "Sarasavi Publishers", 264),
        si("විරාගය", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1956, "891.48", "Sarasavi Publishers", 312),
        si("මඩොල් දූව", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1947, "808.068", "Sarasavi Publishers", 184),
        si(
            "ගොළු හදවත",
            "කරුණාසේන ජයලත්",
            "JAY",
            1962,
            "891.48",
            "Dayawansa Jayakody & Company",
            220),
        si(
            "මළගිය ඇත්තෝ",
            "එදිරිවීර සරච්චන්ද්‍ර",
            "SAR",
            1959,
            "891.48",
            "Godage International Publishers",
            176),
        si(
            "හත් පණ",
            "කුමාරතුංග මුනිදාස",
            "KUM",
            1921,
            "808.068",
            "Godage International Publishers",
            96),
        si(
            "අඹ යහළුවෝ",
            "ටී. බී. ඉලංගරත්න",
            "ILA",
            1957,
            "891.48",
            "Godage International Publishers",
            240),
        si("බවතරණය", "මාර්ටින් වික්‍රමසිංහ", "WIC", 1973, "891.48", "Sarasavi Publishers", 360),
        // Tamil
        ta(
            "பொன்னியின் செல்வன்",
            "கல்கி கிருஷ்ணமூர்த்தி",
            "KAL",
            1955,
            "894.811",
            "Kumaran Book House",
            2400),
        ta(
            "சிவகாமியின் சபதம்",
            "கல்கி கிருஷ்ணமூர்த்தி",
            "KAL",
            1948,
            "894.811",
            "Kumaran Book House",
            960),
        ta(
            "பார்த்திபன் கனவு",
            "கல்கி கிருஷ்ணமூர்த்தி",
            "KAL",
            1943,
            "894.811",
            "Kumaran Book House",
            420),
        ta("திருக்குறள்", "திருவள்ளுவர்", "THI", null, "894.811", "Kumaran Book House", 320),
        ta("சிலப்பதிகாரம்", "இளங்கோ அடிகள்", "ILA", null, "894.811", "Kumaran Book House", 480),
        ta(
            "பஞ்சதந்திரக் கதைகள்",
            "விஷ்ணு சர்மா",
            "VIS",
            null,
            "808.068",
            "Kumaran Book House",
            160),
        // English classics and Sri Lankan writing in English
        en("Pride and Prejudice", "Jane Austen", "AUS", 1813, "823", "Penguin Classics", 432),
        en("Great Expectations", "Charles Dickens", "DIC", 1861, "823", "Penguin Classics", 544),
        en("Jane Eyre", "Charlotte Brontë", "BRO", 1847, "823", "Penguin Classics", 624),
        en("Frankenstein", "Mary Shelley", "SHE", 1818, "823", "Penguin Classics", 280),
        en(
            "The Adventures of Sherlock Holmes",
            "Arthur Conan Doyle",
            "DOY",
            1892,
            "823",
            "Penguin Classics",
            320),
        en("Treasure Island", "Robert Louis Stevenson", "STE", 1883, "808.83", "Puffin Books", 304),
        en(
            "Alice's Adventures in Wonderland",
            "Lewis Carroll",
            "CAR",
            1865,
            "808.068",
            "Puffin Books",
            192),
        en("The Jungle Book", "Rudyard Kipling", "KIP", 1894, "808.068", "Puffin Books", 256),
        en("Funny Boy", "Shyam Selvadurai", "SEL", 1994, "823", "Vijitha Yapa Publications", 320),
        en(
            "Running in the Family",
            "Michael Ondaatje",
            "OND",
            1982,
            "823",
            "Vijitha Yapa Publications",
            208),
        en(
            "Anil's Ghost",
            "Michael Ondaatje",
            "OND",
            2000,
            "823",
            "Vijitha Yapa Publications",
            320),
        en("Reef", "Romesh Gunesekera", "GUN", 1994, "823", "Vijitha Yapa Publications", 192),
        en("Chinaman", "Shehan Karunatilaka", "KAR", 2011, "823", "Vijitha Yapa Publications", 400),
        en(
            "The Seven Moons of Maali Almeida",
            "Shehan Karunatilaka",
            "KAR",
            2022,
            "823",
            "Vijitha Yapa Publications",
            400),
        en(
            "On the Origin of Species",
            "Charles Darwin",
            "DAR",
            1859,
            "576",
            "Oxford University Press",
            512),
        en("A Brief History of Time", "Stephen Hawking", "HAW", 1988, "520", "Bantam Books", 256),
        en("The Dhammapada", "Eknath Easwaran", "EAS", 1985, "294.3", "Penguin Classics", 288),
        en("The Bhagavad Gita", "Eknath Easwaran", "EAS", 1985, "294.5", "Penguin Classics", 296),
        en(
            "A History of Sri Lanka",
            "K. M. de Silva",
            "SIL",
            1981,
            "954.93",
            "Vijitha Yapa Publications",
            784),
        en(
            "The Story of Ceylon",
            "E. F. C. Ludowyk",
            "LUD",
            1962,
            "954.93",
            "Godage International Publishers",
            320),
        new Sample(
            "Oxford Advanced Learner's Dictionary",
            null,
            "A. S. Hornby",
            "HOR",
            "en",
            2020,
            "423",
            "Oxford University Press",
            1952,
            true),
        new Sample(
            "The Sinhala-English Dictionary",
            null,
            "Charles Carter",
            "CAR",
            "en",
            1924,
            "423",
            "Godage International Publishers",
            806,
            true),
        new Sample(
            "The World Book Encyclopedia",
            "Volume 1: A",
            "World Book Editors",
            "WOR",
            "en",
            2021,
            "030",
            "World Book",
            1056,
            true));
  }

  private static Sample si(
      String title,
      String author,
      String cutter,
      Integer year,
      String ddc,
      String publisher,
      int pages) {
    return new Sample(title, null, author, cutter, "si", year, ddc, publisher, pages, false);
  }

  private static Sample ta(
      String title,
      String author,
      String cutter,
      Integer year,
      String ddc,
      String publisher,
      int pages) {
    return new Sample(title, null, author, cutter, "ta", year, ddc, publisher, pages, false);
  }

  private static Sample en(
      String title,
      String author,
      String cutter,
      Integer year,
      String ddc,
      String publisher,
      int pages) {
    return new Sample(title, null, author, cutter, "en", year, ddc, publisher, pages, false);
  }

  // --- Generated books --------------------------------------------------------------------------

  // Author names in their own script, with the Latin "cutter" letters used on shelf labels
  private static final String[][] SI_AUTHORS = {
    {"නිමල් පෙරේරා", "PER"}, {"කුමාරි සිල්වා", "SIL"}, {"සුනිල් ජයසූරිය", "JAY"},
    {"චන්ද්‍රා බණ්ඩාර", "BAN"}, {"අජිත් ගුණවර්ධන", "GUN"}, {"දමයන්ති විජේසිංහ", "WIJ"},
    {"රංජිත් ධර්මසේන", "DHA"}, {"සෙනෙහෙලතා කුමාරසිංහ", "KUM"}
  };
  private static final String[][] TA_AUTHORS = {
    {"சிவா ராஜேந்திரன்", "RAJ"}, {"கமலா சுப்பிரமணியம்", "SUB"}, {"ரவி கணேசன்", "GAN"},
    {"மீனா தேவராஜா", "DEV"}, {"குமார் பாலசுந்தரம்", "BAL"}, {"லதா நடராஜா", "NAD"}
  };
  private static final String[][] EN_AUTHORS = {
    {"Nadia Fernando", "FER"}, {"Ruwan Weerasinghe", "WEE"}, {"Priya Navaratnam", "NAV"},
    {"David Collins", "COL"}, {"Amali de Mel", "MEL"}, {"Sarah Whitfield", "WHI"},
    {"Kavinda Herath", "HER"}, {"Thilini Abeywickrama", "ABE"}
  };
  private static final String[] SI_WORDS = {
    "සඳ", "මල්", "ගඟ", "වැස්ස", "හීන", "පහන", "කඳු", "මුහුද", "ඉර", "දිය"
  };
  private static final String[] SI_ENDINGS = {"කතාව", "ගමන", "සෙවණ", "උරුමය", "රේඛා", "හඬ"};
  private static final String[] TA_WORDS = {
    "நிலா", "மழை", "கடல்", "பூ", "கனவு", "தீபம்", "மலை", "நதி"
  };
  private static final String[] TA_ENDINGS = {"கதை", "பயணம்", "நிழல்", "வீடு", "ஓசை"};
  private static final String[] EN_ADJECTIVES = {
    "Silent", "Golden", "Hidden", "Last", "Monsoon", "Distant", "Painted", "Forgotten"
  };
  private static final String[] EN_NOUNS = {
    "River",
    "Lantern",
    "Island",
    "Garden",
    "Harbour",
    "Letters",
    "Elephant",
    "Tea Estate",
    "Kingdom",
    "Season"
  };

  /** Non-fiction topics: title, Dewey number. */
  private static final String[][] TOPICS = {
    {"Mathematics", "510"},
    {"Computer Programming", "005"},
    {"Agriculture in Sri Lanka", "630"},
    {"Sri Lankan Rice and Curry", "641.5"},
    {"Buddhist Philosophy", "294.3"},
    {"World Geography", "910"},
    {"Economics", "330"},
    {"Psychology", "150"},
    {"Kandyan Dance", "793"},
    {"Cricket", "796"},
    {"Astronomy", "520"},
    {"The History of Sri Lanka", "954.93"}
  };

  private static final String[] TOPIC_PREFIXES = {
    "An Introduction to", "Understanding", "A Guide to", "Basic"
  };
  private static final String[] PUBLISHERS = {
    "Sarasavi Publishers", "Godage International Publishers", "Vijitha Yapa Publications",
    "Dayawansa Jayakody & Company", "Kumaran Book House", "Oxford University Press"
  };

  /** A made-up but plausible book; the mix rotates through fiction in 3 languages and topics. */
  private Sample generated(int index) {
    String publisher = pick(PUBLISHERS);
    int year = 1980 + random.nextInt(46);
    int pages = 80 + random.nextInt(420);
    switch (index % 5) {
      case 0 -> {
        String[] author = pick(SI_AUTHORS);
        String title = pick(SI_WORDS) + " " + pick(SI_ENDINGS);
        return new Sample(
            title, null, author[0], author[1], "si", year, "891.48", publisher, pages, false);
      }
      case 1 -> {
        String[] author = pick(TA_AUTHORS);
        String title = pick(TA_WORDS) + " " + pick(TA_ENDINGS);
        return new Sample(
            title, null, author[0], author[1], "ta", year, "894.811", publisher, pages, false);
      }
      case 2 -> {
        String[] author = pick(EN_AUTHORS);
        String title = "The " + pick(EN_ADJECTIVES) + " " + pick(EN_NOUNS);
        // Every few: a children's or teen book
        String ddc = index % 15 == 2 ? "808.068" : index % 15 == 7 ? "808.83" : "823";
        return new Sample(
            title, null, author[0], author[1], "en", year, ddc, publisher, pages, false);
      }
      default -> {
        String[] author = pick(EN_AUTHORS);
        String[] topic = pick(TOPICS);
        String title = pick(TOPIC_PREFIXES) + " " + topic[0];
        return new Sample(
            title, null, author[0], author[1], "en", year, topic[1], publisher, pages, false);
      }
    }
  }

  private <T> T pick(T[] options) {
    return options[random.nextInt(options.length)];
  }

  // --- CSV for the importer ---------------------------------------------------------------------

  private String csv(List<Sample> samples) {
    StringBuilder out =
        new StringBuilder(String.join(",", CatalogImportService.COLUMNS)).append('\n');
    for (Sample s : samples) {
      int copies = s.reference() ? 1 : 1 + random.nextInt(4);
      String row =
          Stream.of(
                  isbn(),
                  s.title(),
                  s.subtitle(),
                  s.authors(),
                  s.publisher(),
                  s.year() == null ? null : s.year().toString(),
                  String.valueOf(s.pages()),
                  s.language(),
                  "BOOK",
                  s.ddc() + " " + s.cutter(),
                  s.ddc(),
                  String.valueOf(copies),
                  shelf(s) + ", shelf " + (1 + random.nextInt(12)),
                  (400 + random.nextInt(3100)) + ".00",
                  "PURCHASE",
                  s.reference() ? "REFERENCE_ONLY" : "LENDABLE")
              .map(DevCatalogSeeder::quoted)
              .collect(Collectors.joining(","));
      out.append(row).append('\n');
    }
    return out.toString();
  }

  private static String shelf(Sample s) {
    return switch (s.ddc()) {
      case "891.48" -> "Sinhala fiction";
      case "894.811" -> "Tamil fiction";
      case "823" -> "English fiction";
      case "808.068" -> "Children's corner";
      case "808.83" -> "Teen corner";
      case "423", "030" -> "Reference room";
      default -> "Non-fiction " + s.ddc().charAt(0) + "00s";
    };
  }

  private static String quoted(String value) {
    return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
  }

  /** An invented ISBN-13 on the Sri Lankan prefix 978-955, with a correct check digit. */
  private String isbn() {
    StringBuilder digits = new StringBuilder("978955");
    for (int i = 0; i < 6; i++) {
      digits.append(random.nextInt(10));
    }
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      sum += (digits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
    }
    return digits.append((10 - sum % 10) % 10).toString();
  }
}
