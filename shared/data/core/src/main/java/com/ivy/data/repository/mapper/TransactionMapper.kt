package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.common.Value
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.model.primitive.TagId
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class TransactionMapper @Inject constructor() {

    @Suppress("CyclomaticComplexMethod")
    fun TransactionEntity.toDomain(
        accountAssetCode: AssetCode?,
        toAccountAssetCode: AssetCode? = null,
        tags: List<TagId> = emptyList()
    ): Either<String, com.ivy.data.model.Transaction> = either {
        val metadata = com.ivy.data.model.TransactionMetadata(
            recurringRuleId = recurringRuleId,
            loanId = loanId,
            loanRecordId = loanRecordId
        )
        val zoneId = ZoneId.systemDefault()
        val settled = dateTime != null
        val time = dateTime?.atZone(zoneId)?.toInstant()
            ?: dueDate?.atZone(zoneId)?.toInstant()
            ?: raise("Missing transaction time for entity: ${this@toDomain}")

        val fromValue = Value(
            amount = PositiveDouble.from(amount).bind(),
            asset = accountAssetCode
                ?: raise("No asset code associated with the account for this transaction '${this@toDomain}'")
        )

        val notBlankTrimmedTitle = title?.let(NotBlankTrimmedString::from)?.getOrNull()
        val notBlankTrimmedDescription = description?.let(NotBlankTrimmedString::from)?.getOrNull()

        when (type) {
            TransactionType.INCOME -> {
                com.ivy.data.model.Income(
                    id = com.ivy.data.model.TransactionId(id),
                    title = notBlankTrimmedTitle,
                    description = notBlankTrimmedDescription,
                    category = categoryId?.let { com.ivy.data.model.CategoryId(it) },
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    lastUpdated = Instant.EPOCH,
                    removed = isDeleted,
                    value = fromValue,
                    account = com.ivy.data.model.AccountId(accountId),
                    tags = tags
                )
            }

            TransactionType.EXPENSE -> {
                com.ivy.data.model.Expense(
                    id = com.ivy.data.model.TransactionId(id),
                    title = notBlankTrimmedTitle,
                    description = notBlankTrimmedDescription,
                    category = categoryId?.let { com.ivy.data.model.CategoryId(it) },
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    lastUpdated = Instant.EPOCH,
                    removed = isDeleted,
                    value = fromValue,
                    account = com.ivy.data.model.AccountId(accountId),
                    tags = tags
                )
            }

            TransactionType.TRANSFER -> {
                val toValue = Value(
                    amount = toAmount?.let { PositiveDouble.from(it).bind() }
                        ?: raise("Missing transfer amount for transaction '${this@toDomain}'"),
                    asset = toAccountAssetCode
                        ?: raise(
                            "No asset code associated with the destination account for this " +
                                    "transaction '${this@toDomain}'"
                        )
                )

                val toAccount = toAccountId?.let(::AccountId) ?: raise(
                    "No destination account id associated" +
                            " with this transaction '${this@toDomain}'"
                )

                if (accountId == toAccount.value) {
                    raise(
                        "Source account id and destination accounts " +
                                "are same with this transaction '${this@toDomain}'"
                    )
                }

                com.ivy.data.model.Transfer(
                    id = com.ivy.data.model.TransactionId(id),
                    title = notBlankTrimmedTitle,
                    description = notBlankTrimmedDescription,
                    category = categoryId?.let { com.ivy.data.model.CategoryId(it) },
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    lastUpdated = Instant.EPOCH,
                    removed = isDeleted,
                    fromAccount = com.ivy.data.model.AccountId(accountId),
                    fromValue = fromValue,
                    toAccount = toAccount,
                    toValue = toValue,
                    tags = tags
                )
            }
        }
    }

    fun com.ivy.data.model.Transaction.toEntity(): TransactionEntity {
        val dateTime = time.atZone(ZoneId.systemDefault()).toLocalDateTime()
        return TransactionEntity(
            accountId = when (this) {
                is com.ivy.data.model.Expense -> account.value
                is com.ivy.data.model.Income -> account.value
                is com.ivy.data.model.Transfer -> fromAccount.value
            },
            type = when (this) {
                is com.ivy.data.model.Expense -> TransactionType.EXPENSE
                is com.ivy.data.model.Income -> TransactionType.INCOME
                is com.ivy.data.model.Transfer -> TransactionType.TRANSFER
            },
            amount = when (this) {
                is com.ivy.data.model.Expense -> value.amount.value
                is com.ivy.data.model.Income -> value.amount.value
                is com.ivy.data.model.Transfer -> fromValue.amount.value
            },
            toAccountId = if (this is com.ivy.data.model.Transfer) {
                toAccount.value
            } else {
                null
            },
            toAmount = if (this is com.ivy.data.model.Transfer) {
                toValue.amount.value
            } else {
                null
            },
            title = title?.value,
            description = description?.value,
            dateTime = dateTime,
            categoryId = category?.value,
            dueDate = dateTime.takeIf { !settled },
            recurringRuleId = metadata.recurringRuleId,
            attachmentUrl = null,
            loanId = metadata.loanId,
            loanRecordId = metadata.loanRecordId,
            isSynced = true,
            isDeleted = removed,
            id = id.value
        )
    }
}