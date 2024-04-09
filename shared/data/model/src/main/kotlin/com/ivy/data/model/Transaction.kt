package com.ivy.data.model

import com.ivy.data.model.common.Value
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.TagId
import com.ivy.data.model.sync.Syncable
import com.ivy.data.model.sync.UniqueId
import java.time.Instant
import java.util.UUID

@JvmInline
value class TransactionId(override val value: UUID) : UniqueId

sealed interface Transaction : Syncable {
    override val id: TransactionId
    val title: NotBlankTrimmedString?
    val description: NotBlankTrimmedString?
    val category: CategoryId?
    val time: Instant
    val settled: Boolean
    val metadata: TransactionMetadata

    // TODO: Get rid of Tags from the core model because of perf. and complexity
    val tags: List<TagId>
}

data class Income(
    override val id: TransactionId,
    override val title: NotBlankTrimmedString?,
    override val description: NotBlankTrimmedString?,
    override val category: CategoryId?,
    override val time: Instant,
    override val settled: Boolean,
    override val metadata: TransactionMetadata,
    override val lastUpdated: Instant,
    override val removed: Boolean,
    override val tags: List<TagId>,
    val value: Value,
    val account: AccountId,
) : Transaction

data class Expense(
    override val id: TransactionId,
    override val title: NotBlankTrimmedString?,
    override val description: NotBlankTrimmedString?,
    override val category: CategoryId?,
    override val time: Instant,
    override val settled: Boolean,
    override val metadata: TransactionMetadata,
    override val lastUpdated: Instant,
    override val removed: Boolean,
    override val tags: List<TagId>,
    val value: Value,
    val account: AccountId,
) : Transaction

data class Transfer(
    override val id: TransactionId,
    override val title: NotBlankTrimmedString?,
    override val description: NotBlankTrimmedString?,
    override val category: CategoryId?,
    override val time: Instant,
    override val settled: Boolean,
    override val metadata: TransactionMetadata,
    override val lastUpdated: Instant,
    override val removed: Boolean,
    override val tags: List<TagId>,
    val fromAccount: AccountId,
    val fromValue: Value,
    val toAccount: AccountId,
    val toValue: Value,
) : Transaction

@Suppress("DataClassTypedIDs")
data class TransactionMetadata(
    val recurringRuleId: UUID?,
    // This refers to the loan id that is linked with a transaction
    val loanId: UUID? = null,
    // This refers to the loan record id that is linked with a transaction
    val loanRecordId: UUID?,
)