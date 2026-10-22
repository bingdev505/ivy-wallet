package com.ivy.onboarding.steps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.navigation.navigation
import com.ivy.onboarding.components.OnboardingProgressSlider
import com.ivy.onboarding.components.OnboardingToolbar
import com.ivy.onboarding.components.Suggestions
import com.ivy.ui.R
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.wallet.ui.theme.GradientIvy
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.IvyDark
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.OrangeLight
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.RedLight
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.GradientCutBottom
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.OnboardingButton
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.modal.edit.CategoryModal
import com.ivy.wallet.ui.theme.modal.edit.CategoryModalData
import com.ivy.wallet.ui.theme.toComposeColor
import java.util.UUID

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.OnboardingCategories(
    suggestions: List<CreateCategoryData>,
    categories: List<Category>,

    onCreateCategory: (CreateCategoryData) -> Unit = { },
    onEditCategory: (Category) -> Unit = { _ -> },

    onSkip: () -> Unit = {},
    onDoneClick: () -> Unit = {}
) {
    var categoryModalData: CategoryModalData? by remember { mutableStateOf(null) }

    val nav = navigation()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        stickyHeader {
            OnboardingToolbar(
                hasSkip = categories.isEmpty(),
                onBack = { nav.onBackPressed() },
                onSkip = onSkip
            )
        }

        item {
            Column {
                Spacer(Modifier.height(8.dp))

                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(R.string.add_categories),
                    style = UI.typo.h2.style(
                        fontWeight = FontWeight.Black
                    )
                )

//                PremiumInfo(
//                    itemLabelPlural = "categories",
//                    itemsCount = categories.size,
//                    freeItemsCount = Constants.FREE_CATEGORIES
//                )

                if (categories.isEmpty()) {
                    Spacer(Modifier.height(16.dp))

                    Image(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.onboarding_illustration_categories),
                        contentDescription = "categories illustration"
                    )

                    OnboardingProgressSlider(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        selectedStep = 3,
                        stepsCount = 4,
                        selectedColor = IvyDark
                    )

                    Spacer(Modifier.height(48.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }

                var selectedCategoryType: com.ivy.data.model.CategoryType by remember {
                    mutableStateOf(com.ivy.data.model.CategoryType.EXPENSE)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = selectedCategoryType == com.ivy.data.model.CategoryType.EXPENSE,
                        onClick = { selectedCategoryType = com.ivy.data.model.CategoryType.EXPENSE },
                        label = {
                            Text(
                                text = stringResource(R.string.expense),
                                fontWeight = if (selectedCategoryType == com.ivy.data.model.CategoryType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = selectedCategoryType == com.ivy.data.model.CategoryType.INCOME,
                        onClick = { selectedCategoryType = com.ivy.data.model.CategoryType.INCOME },
                        label = {
                            Text(
                                text = stringResource(R.string.income),
                                fontWeight = if (selectedCategoryType == com.ivy.data.model.CategoryType.INCOME) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                val filteredCategories = categories.filter {
                    it.transactionType == selectedCategoryType || it.transactionType == null
                }

                if (filteredCategories.isNotEmpty()) {
                    Categories(
                        categories = filteredCategories,
                        onClick = {
                            categoryModalData = CategoryModalData(
                                category = it,
                                transactionType = it.transactionType ?: selectedCategoryType
                            )
                        }
                    )
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = if (selectedCategoryType == com.ivy.data.model.CategoryType.EXPENSE) {
                        "Expense Suggestions"
                    } else {
                        "Income Suggestions"
                    },
                    style = UI.typo.b1.style(
                        fontWeight = FontWeight.ExtraBold
                    )
                )

                Spacer(Modifier.height(16.dp))

                val filteredSuggestions = suggestions.filter { suggestion ->
                    (suggestion.transactionType == selectedCategoryType || suggestion.transactionType == null) &&
                    categories.none { it.name.value.equals(suggestion.name, ignoreCase = true) }
                }

                Suggestions(
                    suggestions = filteredSuggestions,
                    onAddSuggestion = {
                        val suggestionData = it as CreateCategoryData
                        onCreateCategory(
                            if (suggestionData.transactionType == null) {
                                suggestionData.copy(transactionType = selectedCategoryType)
                            } else {
                                suggestionData
                            }
                        )
                    },
                    onAddNew = {
                        categoryModalData = CategoryModalData(
                            category = null,
                            transactionType = selectedCategoryType
                        )
                    }
                )

                Spacer(Modifier.height(96.dp))
            }
        }
    }

    GradientCutBottom(
        height = 96.dp
    )

    if (categories.isNotEmpty()) {
        OnboardingButton(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),

            text = stringResource(R.string.finish),
            textColor = White,
            backgroundGradient = GradientIvy,
            hasNext = false,
            enabled = true
        ) {
            onDoneClick()
        }
    }

    CategoryModal(
        modal = categoryModalData,
        onCreateCategory = onCreateCategory,
        onEditCategory = onEditCategory,
        dismiss = {
            categoryModalData = null
        }
    )
}

@Composable
private fun Categories(
    categories: List<Category>,
    onClick: (Category) -> Unit
) {
    for (category in categories) {
        CategoryCard(
            category = category
        ) {
            onClick(category)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onClick: () -> Unit
) {
    val categoryColor = category.color.value.toComposeColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r3)
            .background(UI.colors.medium, UI.shapes.r3)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))

        ItemIconSDefaultIcon(
            modifier = Modifier
                .background(categoryColor, CircleShape),
            iconName = category.icon?.id,
            defaultIcon = R.drawable.ic_custom_category_s,
            tint = findContrastTextColor(categoryColor)
        )

        Spacer(Modifier.width(16.dp))

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp)
                .padding(vertical = 24.dp),
            text = category.name.value,
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold
            )
        )

        category.transactionType?.let { type ->
            Text(
                modifier = Modifier
                    .clip(UI.shapes.rFull)
                    .background(UI.colors.pureInverse.copy(alpha = 0.08f), UI.shapes.rFull)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                text = if (type == com.ivy.data.model.CategoryType.EXPENSE) stringResource(R.string.expense) else stringResource(R.string.income),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Medium,
                    color = UI.colors.pureInverse.copy(alpha = 0.6f)
                )
            )
        }

        Spacer(Modifier.width(16.dp))
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Empty() {
    IvyWalletPreview {
        OnboardingCategories(
            suggestions = listOf(
                CreateCategoryData(
                    name = "Food & Drinks",
                    color = Green,
                    icon = "fooddrink"
                ),

                CreateCategoryData(
                    name = "Bills & Fees",
                    color = Red,
                    icon = "bills"
                ),

                CreateCategoryData(
                    name = "Transport",
                    color = OrangeLight,
                    icon = "transport"
                ),

                CreateCategoryData(
                    name = "Groceries",
                    color = Color(0xFF75ff4d),
                    icon = "groceries"
                ),

                CreateCategoryData(
                    name = "Entertainment",
                    color = Orange,
                    icon = "game"
                ),

                CreateCategoryData(
                    name = "Shopping",
                    color = Ivy,
                    icon = "shopping"
                ),

                CreateCategoryData(
                    name = "Gifts",
                    color = RedLight,
                    icon = "gift"
                ),

                CreateCategoryData(
                    name = "Health",
                    color = Color(0xFF4dfff3),
                    icon = "health"
                ),

                CreateCategoryData(
                    name = "Investments",
                    color = Color(0xFF1e5166),
                    icon = "leaf"
                ),
            ),
            categories = listOf()
        )
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Categories() {
    IvyWalletPreview {
        OnboardingCategories(
            suggestions = listOf(
                CreateCategoryData(
                    name = "Food & Drinks",
                    color = Green,
                    icon = "fooddrink"
                ),

                CreateCategoryData(
                    name = "Bills & Fees",
                    color = Red,
                    icon = "bills"
                ),

                CreateCategoryData(
                    name = "Transport",
                    color = OrangeLight,
                    icon = "transport"
                ),

                CreateCategoryData(
                    name = "Groceries",
                    color = Color(0xFF75ff4d),
                    icon = "groceries"
                ),

                CreateCategoryData(
                    name = "Entertainment",
                    color = Orange,
                    icon = "game"
                ),

                CreateCategoryData(
                    name = "Shopping",
                    color = Ivy,
                    icon = "shopping"
                ),

                CreateCategoryData(
                    name = "Gifts",
                    color = RedLight,
                    icon = "gift"
                ),

                CreateCategoryData(
                    name = "Health",
                    color = Color(0xFF4dfff3),
                    icon = "health"
                ),

                CreateCategoryData(
                    name = "Investments",
                    color = Color(0xFF1e5166),
                    icon = "leaf"
                ),
            ),
            categories = listOf(
                Category(
                    name = NotBlankTrimmedString.unsafe("Food & Drinks"),
                    color = ColorInt(Orange.toArgb()),
                    icon = IconAsset.unsafe("fooddrinks"),
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                )
            )
        )
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Premium() {
    IvyWalletPreview {
        OnboardingCategories(
            suggestions = listOf(
                CreateCategoryData(
                    name = "Food & Drinks",
                    color = Green,
                    icon = "fooddrink"
                ),

                CreateCategoryData(
                    name = "Bills & Fees",
                    color = Red,
                    icon = "bills"
                ),

                CreateCategoryData(
                    name = "Transport",
                    color = OrangeLight,
                    icon = "transport"
                ),

                CreateCategoryData(
                    name = "Groceries",
                    color = Color(0xFF75ff4d),
                    icon = "groceries"
                ),

                CreateCategoryData(
                    name = "Entertainment",
                    color = Orange,
                    icon = "game"
                ),

                CreateCategoryData(
                    name = "Shopping",
                    color = Ivy,
                    icon = "shopping"
                ),

                CreateCategoryData(
                    name = "Gifts",
                    color = RedLight,
                    icon = "gift"
                ),

                CreateCategoryData(
                    name = "Health",
                    color = Color(0xFF4dfff3),
                    icon = "health"
                ),

                CreateCategoryData(
                    name = "Investments",
                    color = Color(0xFF1e5166),
                    icon = "leaf"
                ),
            ),
            categories = List(12) {
                Category(
                    name = NotBlankTrimmedString.unsafe("Food & Drinks"),
                    color = ColorInt(Orange.toArgb()),
                    icon = IconAsset.unsafe("fooddrinks"),
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                )
            }
        )
    }
}
