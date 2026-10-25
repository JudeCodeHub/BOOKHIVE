# 0003. Catalog model: Book (title) vs Copy (item)

- Status: Accepted
- Date: 2026-10-25
- Related: checklist steps 147–152, PRD §5.3 (CAT-1, CAT-2, CAT-6, CAT-7, CAT-9), §4.2–4.3

## Context

A library owns **titles** and **physical items**. "Gamperaliya by Martin Wickramasinghe" is one title; the library may own five copies of it: three on the adult fiction shelf, one in reference, and one so worn it's waiting for repair.

These two things answer different questions:

| Question | Who asks | Belongs to |
|---|---|---|
| "Do you have Gamperaliya? Who wrote it? What's it about?" | Member searching | the **title** |
| "Is one on the shelf now? Where?" | Member, desk | the **copies** (counted per title) |
| "Which exact item did Nimali borrow, and when is it due?" | Desk, fines | one **copy** |
| "Put me in the queue for it" | Member | the **title** (any copy will do) |
| "This item was damaged / lost / donated" | Staff | one **copy** |

If both are mixed into one table, every copy repeats the title, authors and description. Search then finds the same book five times, and fixing a typo means editing five rows.

## Decision

Two levels, the standard model in library systems (FRBR "manifestation" vs "item", MARC bibliographic vs holdings records):

### Book = the title (`books`)

Everything that is the same for every copy:

- title, subtitle, ISBN-10/13, edition, publish year, pages, language, format, description, cover, DDC **call number**;
- links to **authors** (`book_authors`, with role such as author/translator/illustrator and the order shown) and **categories** (`book_categories`);
- one **publisher**.

This is what search indexes (`search_vector`) and what members see on a book page. One search hit per title, with a count like "2 of 3 available".

### Copy = one physical item (`copies`)

Everything that differs between items:

- **barcode** (unique; what the desk scans);
- **status**: `AVAILABLE`, `ON_LOAN`, `ON_HOLD_SHELF`, `IN_TRANSIT`, `IN_REPAIR`, `LOST`, `WITHDRAWN`;
- **lending type**: `LENDABLE`, `REFERENCE_ONLY` (never leaves the library), `RESTRICTED` (needs staff approval) (CAT-7);
- condition, shelf location, and acquisition (date, source such as purchase or donation, price, which is also the default replacement cost for a lost item).

Every status change is written to `copy_status_history` (who, when, from → to, why) (CAT-9).

### What links to which level

| Record | Points to | Why |
|---|---|---|
| Loan | **copy** | a specific item leaves the building and must come back |
| Hold | **book** | the member wants the title; the first copy returned is assigned to them (`assigned_copy_id`) |
| Fine | loan (so the copy) | charged for a specific late, lost or damaged item |
| Review, wishlist, suggestion | **book** | opinions and wishes are about the title |
| Availability count | computed from copies | never stored on the book, so it can't drift out of date |

### Supporting tables

- **authors**: name and bio. Shared by all the books they wrote, so "all books by this author" is one query.
- **publishers**: name.
- **categories**: a tree (`parent_id`) following the **Dewey Decimal Classification** (e.g. 800 Literature → 891 East Indo-European → 891.48 Sinhala literature). Each category has a library **section**: `ADULT`, `CHILDREN`, `YOUNG_ADULT` or `REFERENCE`.
  - The PRD writes `YA`; we use `YOUNG_ADULT`, the code already used by the Junior membership type's allowed sections (V8 seed).
  - A book's section comes from its categories, and `MembershipType.allowsSection()` uses it to keep juniors to the children's and young-adult shelves.

## Alternatives considered

- **One table, one row per copy.** Simple at first, but search returns duplicates, metadata gets copied everywhere, and holds have no natural "title" to queue on. Rejected.
- **A third level for works** (FRBR "work": Gamperaliya in Sinhala and its English translation as one work). Useful for big academic catalogs, but too heavy for a public library. A translation is a separate book with the translator as an author role. We can add a `work_id` later if needed.
- **Storing availability counts on `books`.** Faster to read, but every checkout, return and status change would have to update it, and a missed update shows wrong numbers. Counted from `copies` instead (indexed by `book_id, status`), and cached briefly in search later if needed.
- **Authors as plain text on the book.** Easy to type, but "Martin Wickramasinghe" vs "M. Wickramasinghe" become two people, and author pages don't work. Rejected in favour of an `authors` table.

## Consequences

- Cataloguing is two steps: create (or find by ISBN) the **book**, then add **copies** with barcodes. The ISBN lookup (CAT-3) fills the book; adding a copy is then just "scan the new barcode".
- The desk always works with **copies** (scan barcode → loan); members always work with **books** (search, hold, review).
- Checkout and holds must check the copy's **lending type** and **status**, and the member type's **sections**.
- Deleting is rare. A book with copies or loan history isn't deleted; copies are marked `WITHDRAWN`, so history and fines stay correct.
