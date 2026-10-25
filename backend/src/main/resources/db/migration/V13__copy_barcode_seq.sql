-- Copy barcodes C0000001, C0000002... (checklist step 160). A sequence never gives the same
-- number twice, even when two librarians add copies at the same moment. Staff can still type
-- an existing label's barcode instead.
CREATE SEQUENCE copy_barcode_seq START WITH 1 INCREMENT BY 1;
