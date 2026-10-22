# Updates and new features

This community update builds on Ivy Wallet by Ivy Apps. The original repository is archived. Original copyright notices and the GPL-3.0 license remain in place.

## Payment notifications and transaction inbox

- Capture supported payment notifications and SMS messages with the required Android permissions.
- Review detected payments in a transaction inbox, with read and dismiss actions.
- Prefill the amount, currency, recipient or merchant, and original message when reviewing a payment.
- Suppress duplicate captures and ignore Ivy Wallet's own generated alerts.
- Consume matching duplicate messages when a reviewed payment is saved.
- Preserve the detected amount and currency when another account is selected, and require a matching account before saving a captured payment.
- Handle notification taps when the app is already open and allow returning from the inbox to Home.

## Loans and repayments

- Choose Loan alongside Expense, Income and Transfer using matching transaction-type cards.
- Reuse the existing loan and loan-record forms from More → Loans.
- Fill the detected person's name and payment amount when opening a loan form.
- Match existing loans by name and currency. Suggest repayment of outstanding debt before another loan addition; offer a picker when several loans match.
- Update an existing person's loan with increase or repayment records instead of creating an unnecessary duplicate.
- Remember reviewed recipient-name aliases and Expense/Income/Loan choices for future payments, separately by currency and payment direction.
- Show Review loan, Review expense or Review income in payment alerts and the inbox.
- Keep loan cash movements separate from ordinary income and expense totals through linked transfers.
- Validate payment direction, currency and repayment amounts, and save the loan, cash movement and captured-message state together.

Suggestions are editable and require a reviewed save. A transfer involving an unknown person remains an ordinary Expense or Income until the user chooses Loan. Bank/app sender names are not treated as detected people. Learned suggestions are stored locally on the device.

## Shared expenses and trip groups

- Split an expense directly on the expense page, with searchable saved people and new names.
- Fill shares equally by default, including when the total arrives from a payment notification. Editing a share switches to custom amounts.
- When someone else pays, record the user's expense and borrowed share. When the user pays for others, record their own expense and amounts lent to the other participants.
- Create reusable trip groups and access them from More using the existing quick-access design.
- View group members, expense history and balances in layouts consistent with category cards.
- Include trip groups and shared-expense records in backup/import handling.

## Categories, suggestions and interface

- Support Expense, Income and shared categories in category creation, editing and onboarding.
- Filter the category picker and title suggestions by transaction type. Show category context beside suggested titles.
- Reuse a previously recorded category when reviewing another ordinary payment to the same merchant.
- Improve split-form spacing, person rows, share fields and removal controls.
- Remove swipe-down-to-open-More from Home, its header and balance section; retain the More button and regular scrolling.
- Include loan search/filter updates, button styling adjustments and loan balance handling already present in this development version.
- Add optional daily local backups and instant-backup settings. Backup archives are kept in app-private storage with a limit of ten archives.

## Data compatibility

The Room database advances from version 130 to 133:

1. **130 → 131:** captured payment messages.
2. **131 → 132:** trip groups and group expenses.
3. **132 → 133:** category transaction types, inferred from compatible existing history where possible.

Existing categories with mixed or insufficient history remain available to both transaction types. Migration schemas and regression tests are included in the source.

## Validation

The development update has been built and installed on a connected Android phone. Coverage includes payment parsing, duplicate prevention, account selection, name aliases, loan direction and repayments, split rounding, transaction rollback, category separation, trip data and backup round trips.

Publication checks on 22 October 2026 passed the app build, 194 core unit tests, 8 transaction-editor unit tests, 3 navigation unit tests and 36 on-device integration tests (241 tests total).

Automatic merchant/person recognition depends on the information provided by the payment app or bank. Ambiguous or incomplete messages still need user correction. These changes are a community development update; the original maintainers do not provide support for this fork.
