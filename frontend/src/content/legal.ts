/**
 * Privacy policy and terms of use (checklist step 213), in the three site languages. Kept here
 * rather than in messages/*.json because they're long documents, not interface text.
 *
 * Written to describe what BookHive actually does (encrypted NICs, ID scans deleted after 90
 * days, private reading history, no card data stored, only login and preference cookies), with
 * Sri Lanka's Personal Data Protection Act No. 9 of 2022 in mind.
 * Have both reviewed by a lawyer before launch; update LAST_UPDATED with any change.
 */
export type LegalDocument = {
  title: string;
  intro: string;
  sections: { heading: string; paragraphs: string[] }[];
};

export type Locale = "en" | "si" | "ta";

export const LAST_UPDATED = "2026-10-26";

export const PRIVACY: Record<Locale, LegalDocument> = {
  en: {
    title: "Privacy policy",
    intro:
      "This policy explains what personal information BookHive collects, why, how we protect it and what your rights are under Sri Lanka's Personal Data Protection Act No. 9 of 2022.",
    sections: [
      {
        heading: "What we collect",
        paragraphs: [
          "Account: your name, email address, optional mobile number and preferred language.",
          "Membership: your date of birth, address, NIC number (or student ID) and a photo or scan of that document, and for junior members the guardian's member number.",
          "Library use: the books you borrow, reserve and return, fines and payments, and messages you send us.",
        ],
      },
      {
        heading: "Why we use it",
        paragraphs: [
          "To run your membership and loans, send you reminders and receipts, check that applications are genuine, keep the library's books safe, and answer your questions. We do not sell your information or use it for advertising.",
        ],
      },
      {
        heading: "How we protect it",
        paragraphs: [
          "NIC numbers are stored encrypted. Photos of ID documents are kept in private storage that only staff can open, and are deleted 90 days after your application is decided. Passwords are stored only as secure hashes. Staff see only what they need for their work, and every important action is logged.",
        ],
      },
      {
        heading: "Your reading history",
        paragraphs: [
          "What you read is private. Staff see your current loans only so they can run the desk. You can switch off your reading history in Settings; returned loans are then made anonymous after 30 days.",
        ],
      },
      {
        heading: "Payments",
        paragraphs: [
          "Online payments are handled by PayHere. We never see or store your card number; we keep only the payment reference and amount.",
        ],
      },
      {
        heading: "Cookies",
        paragraphs: [
          "We use only the cookies the site needs: one that keeps you logged in (it can't be read by other websites), one that tells the site you have a session, and ones that remember your language and light or dark mode. No advertising or tracking cookies.",
          "The contact form is protected by Cloudflare Turnstile, and the map on the visit page comes from OpenStreetMap; these services see your connection when you use those parts of the site.",
        ],
      },
      {
        heading: "How long we keep it",
        paragraphs: [
          "Account and membership details are kept while you are a member and for a short time after, as required for the library's records. Financial records are kept as long as the law requires. You can ask us to close your account.",
        ],
      },
      {
        heading: "Your rights",
        paragraphs: [
          "You can ask to see the personal information we hold about you, to correct it, to delete it where the law allows, or to withdraw consent for optional uses. Most details can be changed yourself in My account. For anything else, contact us through the visit page; we reply within the time the Act sets.",
        ],
      },
    ],
  },
  si: {
    title: "පෞද්ගලිකත්ව ප්‍රතිපත්තිය",
    intro:
      "BookHive එකතු කරන පුද්ගලික තොරතුරු මොනවාද, ඇයි, අපි ඒවා ආරක්ෂා කරන්නේ කෙසේද සහ ශ්‍රී ලංකාවේ 2022 අංක 9 දරන පුද්ගලික දත්ත ආරක්ෂණ පනත යටතේ ඔබේ අයිතිවාසිකම් මොනවාද යන්න මෙම ප්‍රතිපත්තිය පැහැදිලි කරයි.",
    sections: [
      {
        heading: "අප එකතු කරන දේ",
        paragraphs: [
          "ගිණුම: ඔබේ නම, ඊමේල් ලිපිනය, අත්‍යවශ්‍ය නොවන ජංගම දුරකථන අංකය සහ කැමති භාෂාව.",
          "සාමාජිකත්වය: ඔබේ උපන් දිනය, ලිපිනය, ජාතික හැඳුනුම්පත් අංකය (හෝ ශිෂ්‍ය හැඳුනුම්පත) සහ එම ලේඛනයේ ඡායාරූපයක් හෝ ස්කෑන් එකක්, සහ කනිෂ්ඨ සාමාජිකයින් සඳහා භාරකරුගේ සාමාජික අංකය.",
          "පුස්තකාල භාවිතය: ඔබ ණයට ගන්නා, වෙන් කරන සහ ආපසු දෙන පොත්, දඩ සහ ගෙවීම්, සහ ඔබ අපට එවන පණිවිඩ.",
        ],
      },
      {
        heading: "අප එය භාවිත කරන්නේ ඇයි",
        paragraphs: [
          "ඔබේ සාමාජිකත්වය සහ ණය කළමනාකරණය කිරීමට, සිහිකැඳවීම් සහ රිසිට්පත් යැවීමට, අයදුම්පත් සත්‍ය දැයි පරීක්ෂා කිරීමට, පුස්තකාලයේ පොත් ආරක්ෂා කිරීමට සහ ඔබේ ප්‍රශ්නවලට පිළිතුරු දීමට. අපි ඔබේ තොරතුරු විකුණන්නේ හෝ ප්‍රචාරණය සඳහා භාවිත කරන්නේ නැත.",
        ],
      },
      {
        heading: "අප එය ආරක්ෂා කරන්නේ කෙසේද",
        paragraphs: [
          "ජාතික හැඳුනුම්පත් අංක සංකේතනය කර ගබඩා කෙරේ. හැඳුනුම්පත් ඡායාරූප කාර්ය මණ්ඩලයට පමණක් විවෘත කළ හැකි පෞද්ගලික ගබඩාවක තබා, ඔබේ අයදුම්පත තීරණය කර දින 90කට පසු මකා දමනු ලැබේ. මුරපද ගබඩා කරන්නේ ආරක්ෂිත හෑෂ් ලෙස පමණි. කාර්ය මණ්ඩලයට ඔවුන්ගේ කාර්යයට අවශ්‍ය දේ පමණක් පෙනේ, සහ සෑම වැදගත් ක්‍රියාවක්ම සටහන් කෙරේ.",
        ],
      },
      {
        heading: "ඔබේ කියවීමේ ඉතිහාසය",
        paragraphs: [
          "ඔබ කියවන දේ පෞද්ගලිකයි. කවුළුව පවත්වාගෙන යාමට පමණක් කාර්ය මණ්ඩලයට ඔබේ වත්මන් ණය පෙනේ. සැකසුම්වලදී ඔබේ කියවීමේ ඉතිහාසය අක්‍රිය කළ හැක; එවිට ආපසු දුන් ණය දින 30කට පසු නිර්නාමික කෙරේ.",
        ],
      },
      {
        heading: "ගෙවීම්",
        paragraphs: [
          "අන්තර්ජාල ගෙවීම් PayHere මගින් හසුරුවයි. අපි කිසිවිටෙක ඔබේ කාඩ්පත් අංකය දකින්නේ හෝ ගබඩා කරන්නේ නැත; අපි තබා ගන්නේ ගෙවීම් යොමුව සහ මුදල පමණි.",
        ],
      },
      {
        heading: "කුකීස්",
        paragraphs: [
          "අපි භාවිත කරන්නේ වෙබ් අඩවියට අවශ්‍ය කුකීස් පමණි: ඔබව පිවිසී සිටීමට තබන එකක් (වෙනත් වෙබ් අඩවිවලට එය කියවිය නොහැක), ඔබට සැසියක් ඇති බව අඩවියට කියන එකක්, සහ ඔබේ භාෂාව සහ ආලෝක හෝ අඳුරු ප්‍රකාරය මතක තබා ගන්නා ඒවා. ප්‍රචාරණ හෝ ලුහුබැඳීමේ කුකීස් නැත.",
          "සම්බන්ධතා පෝරමය Cloudflare Turnstile මගින් ආරක්ෂා කෙරෙන අතර, පැමිණීමේ පිටුවේ සිතියම OpenStreetMap වෙතින් ලැබේ; ඔබ අඩවියේ එම කොටස් භාවිත කරන විට මෙම සේවාවන්ට ඔබේ සම්බන්ධතාවය පෙනේ.",
        ],
      },
      {
        heading: "අප එය තබා ගන්නා කාලය",
        paragraphs: [
          "ගිණුම් සහ සාමාජිකත්ව විස්තර ඔබ සාමාජිකයෙකු වන තාක් සහ ඉන් පසු කෙටි කාලයක්, පුස්තකාලයේ වාර්තා සඳහා අවශ්‍ය පරිදි තබා ගැනේ. මූල්‍ය වාර්තා නීතිය අවශ්‍ය කරන කාලය තබා ගැනේ. ඔබේ ගිණුම වසා දමන ලෙස ඔබට ඉල්ලා සිටිය හැක.",
        ],
      },
      {
        heading: "ඔබේ අයිතිවාසිකම්",
        paragraphs: [
          "අප ඔබ ගැන තබා ඇති පුද්ගලික තොරතුරු බැලීමට, නිවැරදි කිරීමට, නීතිය ඉඩ දෙන තැන මකා දැමීමට, හෝ අත්‍යවශ්‍ය නොවන භාවිතයන් සඳහා කැමැත්ත ඉවත් කර ගැනීමට ඔබට ඉල්ලා සිටිය හැක. බොහෝ විස්තර මගේ ගිණුමේදී ඔබටම වෙනස් කළ හැක. වෙනත් ඕනෑම දෙයක් සඳහා පැමිණීමේ පිටුව හරහා අප අමතන්න; පනත නියම කරන කාලය තුළ අපි පිළිතුරු දෙමු.",
        ],
      },
    ],
  },
  ta: {
    title: "தனியுரிமைக் கொள்கை",
    intro:
      "BookHive எந்தத் தனிப்பட்ட தகவல்களைச் சேகரிக்கிறது, ஏன், அவற்றை எப்படிப் பாதுகாக்கிறோம், இலங்கையின் 2022 ஆம் ஆண்டின் 9 ஆம் இலக்க தனிப்பட்ட தரவுப் பாதுகாப்புச் சட்டத்தின் கீழ் உங்கள் உரிமைகள் என்ன என்பதை இக்கொள்கை விளக்குகிறது.",
    sections: [
      {
        heading: "நாம் சேகரிப்பவை",
        paragraphs: [
          "கணக்கு: உங்கள் பெயர், மின்னஞ்சல் முகவரி, விருப்பத்தேர்வான கைபேசி எண் மற்றும் விருப்ப மொழி.",
          "உறுப்புரிமை: உங்கள் பிறந்த தேதி, முகவரி, தேசிய அடையாள அட்டை எண் (அல்லது மாணவர் அடையாள அட்டை) மற்றும் அந்த ஆவணத்தின் படம் அல்லது ஸ்கேன், சிறுவர் உறுப்பினர்களுக்குப் பாதுகாவலரின் உறுப்பினர் எண்.",
          "நூலகப் பயன்பாடு: நீங்கள் இரவல் பெறும், முன்பதிவு செய்யும், திருப்பித் தரும் நூல்கள், அபராதங்களும் கட்டணங்களும், நீங்கள் அனுப்பும் செய்திகள்.",
        ],
      },
      {
        heading: "ஏன் பயன்படுத்துகிறோம்",
        paragraphs: [
          "உங்கள் உறுப்புரிமையையும் இரவல்களையும் நிர்வகிக்க, நினைவூட்டல்களையும் ரசீதுகளையும் அனுப்ப, விண்ணப்பங்கள் உண்மையானவையா எனச் சரிபார்க்க, நூலக நூல்களைப் பாதுகாக்க, உங்கள் கேள்விகளுக்குப் பதிலளிக்க. உங்கள் தகவல்களை விற்பதில்லை, விளம்பரத்துக்குப் பயன்படுத்துவதுமில்லை.",
        ],
      },
      {
        heading: "எப்படிப் பாதுகாக்கிறோம்",
        paragraphs: [
          "தேசிய அடையாள அட்டை எண்கள் மறைகுறியாக்கப்பட்டுச் சேமிக்கப்படுகின்றன. அடையாள ஆவணப் படங்கள் பணியாளர்கள் மட்டுமே திறக்கக்கூடிய தனிப்பட்ட சேமிப்பில் வைக்கப்பட்டு, உங்கள் விண்ணப்பம் முடிவு செய்யப்பட்டு 90 நாட்களில் நீக்கப்படும். கடவுச்சொற்கள் பாதுகாப்பான ஹாஷ்களாக மட்டுமே சேமிக்கப்படுகின்றன. பணியாளர்கள் தங்கள் பணிக்குத் தேவையானதை மட்டுமே பார்ப்பார்கள்; ஒவ்வொரு முக்கியச் செயலும் பதிவு செய்யப்படுகிறது.",
        ],
      },
      {
        heading: "உங்கள் வாசிப்பு வரலாறு",
        paragraphs: [
          "நீங்கள் படிப்பது தனிப்பட்டது. கவுண்டரை நடத்த மட்டுமே பணியாளர்கள் உங்கள் தற்போதைய இரவல்களைப் பார்ப்பார்கள். அமைப்புகளில் உங்கள் வாசிப்பு வரலாற்றை அணைக்கலாம்; அப்போது திருப்பித் தந்த இரவல்கள் 30 நாட்களில் அநாமதேயமாக்கப்படும்.",
        ],
      },
      {
        heading: "கட்டணங்கள்",
        paragraphs: [
          "இணையக் கட்டணங்களை PayHere கையாளுகிறது. உங்கள் அட்டை எண்ணை நாம் ஒருபோதும் பார்ப்பதோ சேமிப்பதோ இல்லை; கட்டணக் குறிப்பையும் தொகையையும் மட்டுமே வைத்திருக்கிறோம்.",
        ],
      },
      {
        heading: "குக்கீகள்",
        paragraphs: [
          "இணையதளத்துக்குத் தேவையான குக்கீகளை மட்டுமே பயன்படுத்துகிறோம்: உங்களை உள்நுழைந்த நிலையில் வைக்கும் ஒன்று (பிற இணையதளங்களால் அதைப் படிக்க முடியாது), உங்களுக்கு அமர்வு உள்ளதெனத் தளத்துக்குச் சொல்லும் ஒன்று, உங்கள் மொழியையும் ஒளி அல்லது இருள் பயன்முறையையும் நினைவில் கொள்பவை. விளம்பர அல்லது கண்காணிப்புக் குக்கீகள் இல்லை.",
          "தொடர்புப் படிவம் Cloudflare Turnstile மூலம் பாதுகாக்கப்படுகிறது; பார்வையிடும் பக்கத்திலுள்ள வரைபடம் OpenStreetMap இலிருந்து வருகிறது. தளத்தின் அப்பகுதிகளைப் பயன்படுத்தும்போது இச்சேவைகள் உங்கள் இணைப்பைப் பார்க்கும்.",
        ],
      },
      {
        heading: "எவ்வளவு காலம் வைத்திருக்கிறோம்",
        paragraphs: [
          "கணக்கு மற்றும் உறுப்புரிமை விவரங்கள் நீங்கள் உறுப்பினராக இருக்கும் வரையும், அதன் பின் நூலகப் பதிவுகளுக்குத் தேவையான குறுகிய காலமும் வைக்கப்படும். நிதிப் பதிவுகள் சட்டம் கோரும் காலம் வைக்கப்படும். உங்கள் கணக்கை மூடுமாறு கேட்கலாம்.",
        ],
      },
      {
        heading: "உங்கள் உரிமைகள்",
        paragraphs: [
          "உங்களைப் பற்றி நாம் வைத்திருக்கும் தனிப்பட்ட தகவல்களைப் பார்க்க, திருத்த, சட்டம் அனுமதிக்கும் இடத்தில் நீக்க, அல்லது விருப்பத்தேர்வான பயன்பாடுகளுக்கான சம்மதத்தைத் திரும்பப் பெறக் கேட்கலாம். பெரும்பாலான விவரங்களை என் கணக்கில் நீங்களே மாற்றலாம். வேறு எதற்கும் பார்வையிடும் பக்கம் மூலம் எங்களைத் தொடர்பு கொள்ளுங்கள்; சட்டம் விதிக்கும் காலத்துக்குள் பதிலளிப்போம்.",
        ],
      },
    ],
  },
};

export const TERMS: Record<Locale, LegalDocument> = {
  en: {
    title: "Terms of use",
    intro: "By creating an account or using BookHive's online services you agree to these terms.",
    sections: [
      {
        heading: "Your account",
        paragraphs: [
          "Give correct information and keep your password to yourself. You are responsible for what is done with your account. Tell us at once if you think someone else has used it.",
        ],
      },
      {
        heading: "Membership and borrowing",
        paragraphs: [
          "Membership is approved by library staff, who may ask to see your original documents. Borrowing limits, loan periods, renewals, reservations and fines are set out on the borrowing rules page, which is part of these terms.",
        ],
      },
      {
        heading: "Looking after books",
        paragraphs: [
          "Return items on time and in the condition you received them. Lost or damaged items are charged as described in the borrowing rules. While you owe more than the limit shown there, borrowing is paused.",
        ],
      },
      {
        heading: "Fair use",
        paragraphs: [
          "Don't misuse the website: no attempts to get into other people's accounts, overload the service or collect its data automatically. Messages and suggestions must be respectful. We may suspend accounts that break these terms or the library's rules, with a reason.",
        ],
      },
      {
        heading: "The service",
        paragraphs: [
          "We work to keep the online services available and correct, but availability shown online can change before you reach the library, and the service may sometimes be down for maintenance. The library's decision at the desk is final for loans.",
        ],
      },
      {
        heading: "Changes and law",
        paragraphs: [
          "We may update these terms; the date below shows the latest version, and important changes are announced on the site. These terms are governed by the laws of Sri Lanka.",
        ],
      },
    ],
  },
  si: {
    title: "භාවිත නියම",
    intro: "ගිණුමක් සෑදීමෙන් හෝ BookHive හි අන්තර්ජාල සේවා භාවිත කිරීමෙන් ඔබ මෙම නියමවලට එකඟ වේ.",
    sections: [
      {
        heading: "ඔබේ ගිණුම",
        paragraphs: [
          "නිවැරදි තොරතුරු ලබා දී ඔබේ මුරපදය ඔබ ළඟම තබා ගන්න. ඔබේ ගිණුමෙන් කරන දේ සඳහා ඔබ වගකිව යුතුය. වෙනත් අයෙකු එය භාවිත කළ බව ඔබ සිතන්නේ නම් වහාම අපට දන්වන්න.",
        ],
      },
      {
        heading: "සාමාජිකත්වය සහ ණය ගැනීම",
        paragraphs: [
          "සාමාජිකත්වය පුස්තකාල කාර්ය මණ්ඩලය විසින් අනුමත කරනු ලබන අතර, ඔවුන්ට ඔබේ මුල් ලේඛන බැලීමට ඉල්ලා සිටිය හැක. ණය ගැනීමේ සීමා, ණය කාල, අලුත් කිරීම්, වෙන් කිරීම් සහ දඩ ණය ගැනීමේ නීති පිටුවේ දක්වා ඇති අතර, එය මෙම නියමවල කොටසකි.",
        ],
      },
      {
        heading: "පොත් රැකබලා ගැනීම",
        paragraphs: [
          "අයිතම නියමිත වේලාවට සහ ඔබට ලැබුණු තත්ත්වයෙන් ආපසු දෙන්න. නැති වූ හෝ හානි වූ අයිතම සඳහා ණය ගැනීමේ නීතිවල දක්වා ඇති පරිදි ගාස්තු අය කෙරේ. එහි දක්වා ඇති සීමාවට වඩා ඔබ ණයගැති වන අතරතුර ණය ගැනීම නවතී.",
        ],
      },
      {
        heading: "සාධාරණ භාවිතය",
        paragraphs: [
          "වෙබ් අඩවිය අවභාවිත නොකරන්න: අන් අයගේ ගිණුම්වලට ඇතුළු වීමට, සේවාව අධික ලෙස බර කිරීමට හෝ එහි දත්ත ස්වයංක්‍රීයව එකතු කිරීමට උත්සාහ නොකරන්න. පණිවිඩ සහ යෝජනා ගෞරවනීය විය යුතුය. මෙම නියම හෝ පුස්තකාලයේ නීති කඩ කරන ගිණුම් හේතුවක් සමඟ අපට අත්හිටුවිය හැක.",
        ],
      },
      {
        heading: "සේවාව",
        paragraphs: [
          "අන්තර්ජාල සේවා ලබාගත හැකි සහ නිවැරදි ලෙස තබා ගැනීමට අපි කටයුතු කරමු, නමුත් ඔබ පුස්තකාලයට පැමිණීමට පෙර අන්තර්ජාලයේ පෙන්වන ලබාගත හැකි බව වෙනස් විය හැකි අතර, නඩත්තුව සඳහා සමහර විට සේවාව නතර විය හැක. ණය සම්බන්ධයෙන් කවුළුවේදී පුස්තකාලයේ තීරණය අවසාන වේ.",
        ],
      },
      {
        heading: "වෙනස්කම් සහ නීතිය",
        paragraphs: [
          "අපට මෙම නියම යාවත්කාලීන කළ හැක; පහත දිනය නවතම අනුවාදය පෙන්වන අතර, වැදගත් වෙනස්කම් අඩවියේ නිවේදනය කෙරේ. මෙම නියම ශ්‍රී ලංකාවේ නීති මගින් පාලනය වේ.",
        ],
      },
    ],
  },
  ta: {
    title: "பயன்பாட்டு விதிமுறைகள்",
    intro:
      "கணக்கை உருவாக்குவதன் மூலம் அல்லது BookHive இன் இணையச் சேவைகளைப் பயன்படுத்துவதன் மூலம் இவ்விதிமுறைகளுக்கு ஒப்புக்கொள்கிறீர்கள்.",
    sections: [
      {
        heading: "உங்கள் கணக்கு",
        paragraphs: [
          "சரியான தகவல்களைக் கொடுத்து உங்கள் கடவுச்சொல்லை உங்களிடமே வைத்திருங்கள். உங்கள் கணக்கில் செய்யப்படுவதற்கு நீங்களே பொறுப்பு. வேறு யாராவது அதைப் பயன்படுத்தியதாக நினைத்தால் உடனே எங்களிடம் தெரிவியுங்கள்.",
        ],
      },
      {
        heading: "உறுப்புரிமையும் இரவலும்",
        paragraphs: [
          "உறுப்புரிமையை நூலகப் பணியாளர்கள் அங்கீகரிப்பார்கள்; அவர்கள் உங்கள் மூல ஆவணங்களைப் பார்க்கக் கேட்கலாம். இரவல் வரம்புகள், இரவல் காலங்கள், புதுப்பிப்புகள், முன்பதிவுகள், அபராதங்கள் இரவல் விதிகள் பக்கத்தில் உள்ளன; அது இவ்விதிமுறைகளின் ஒரு பகுதியாகும்.",
        ],
      },
      {
        heading: "நூல்களைப் பேணுதல்",
        paragraphs: [
          "பொருட்களை உரிய நேரத்தில், பெற்ற நிலையிலேயே திருப்பித் தாருங்கள். தொலைந்த அல்லது சேதமடைந்த பொருட்களுக்கு இரவல் விதிகளில் கூறியபடி கட்டணம் விதிக்கப்படும். அங்கு காட்டப்பட்ட வரம்புக்கு மேல் நிலுவை இருக்கும்போது இரவல் நிறுத்தப்படும்.",
        ],
      },
      {
        heading: "நியாயமான பயன்பாடு",
        paragraphs: [
          "இணையதளத்தைத் தவறாகப் பயன்படுத்தாதீர்கள்: பிறரின் கணக்குகளுக்குள் நுழைய, சேவைக்கு அதிகச் சுமை கொடுக்க, அல்லது அதன் தரவுகளைத் தானியங்கியாகச் சேகரிக்க முயலாதீர்கள். செய்திகளும் பரிந்துரைகளும் மரியாதையாக இருக்க வேண்டும். இவ்விதிமுறைகளை அல்லது நூலக விதிகளை மீறும் கணக்குகளைக் காரணத்துடன் இடைநிறுத்தலாம்.",
        ],
      },
      {
        heading: "சேவை",
        paragraphs: [
          "இணையச் சேவைகளைக் கிடைக்கும்படியும் சரியாகவும் வைத்திருக்க முயல்கிறோம்; ஆனால் நீங்கள் நூலகத்தை அடையும் முன் இணையத்தில் காட்டப்படும் கிடைக்கும் நிலை மாறலாம், பராமரிப்புக்காகச் சில சமயம் சேவை நிற்கலாம். இரவல்கள் தொடர்பாகக் கவுண்டரில் நூலகத்தின் முடிவே இறுதியானது.",
        ],
      },
      {
        heading: "மாற்றங்களும் சட்டமும்",
        paragraphs: [
          "இவ்விதிமுறைகளைப் புதுப்பிக்கலாம்; கீழுள்ள தேதி சமீபத்திய பதிப்பைக் காட்டுகிறது, முக்கிய மாற்றங்கள் தளத்தில் அறிவிக்கப்படும். இவ்விதிமுறைகள் இலங்கைச் சட்டங்களால் ஆளப்படுகின்றன.",
        ],
      },
    ],
  },
};
