package com.babyrecipe.service;

import com.babyrecipe.domain.*;
import com.babyrecipe.dto.request.RecipeRequest;
import com.babyrecipe.dto.response.RecipeResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final TagRepository tagRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ImageStorageService imageStorageService;

    public Page<RecipeResponse> getRecipes(Recipe.AgeGroup ageGroup, Recipe.Category category,
                                           String keyword, Pageable pageable, Long currentUserId) {
        return recipeRepository.findWithFilters(ageGroup, category, keyword, pageable)
            .map(r -> toResponse(r, currentUserId));
    }

    public Page<RecipeResponse> getFeed(Long userId, Pageable pageable) {
        return recipeRepository.findFeedByUserId(userId, pageable)
            .map(r -> toResponse(r, userId));
    }

    @Transactional
    public RecipeResponse createRecipe(RecipeRequest request, Long authorId) {
        User author = userRepository.findById(authorId)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));

        Recipe recipe = Recipe.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .imageUrl(request.getImageUrl())
            .ageGroup(request.getAgeGroup())
            .category(request.getCategory())
            .cookingTime(request.getCookingTime())
            .servings(request.getServings())
            .author(author)
            .build();

        addIngredientsToRecipe(recipe, request);
        addStepsToRecipe(recipe, request);
        addTagsToRecipe(recipe, request);

        recipeRepository.save(recipe);
        return toResponse(recipe, authorId);
    }

    public RecipeResponse getRecipe(Long recipeId, Long currentUserId) {
        Recipe recipe = findRecipe(recipeId);
        return toResponse(recipe, currentUserId);
    }

    @Transactional
    public RecipeResponse getRecipeWithView(Long recipeId, Long currentUserId) {
        Recipe recipe = findRecipe(recipeId);
        recipe.incrementViewCount();
        return toResponse(recipe, currentUserId);
    }

    @Transactional
    public RecipeResponse updateRecipe(Long recipeId, RecipeRequest request, Long userId) {
        Recipe recipe = findRecipe(recipeId);
        checkAuthor(recipe, userId);

        recipe.update(request.getTitle(), request.getDescription(), request.getImageUrl(),
            request.getCookingTime(), request.getServings(),
            request.getAgeGroup(), request.getCategory());

        recipe.clearIngredients();
        recipe.clearSteps();
        addIngredientsToRecipe(recipe, request);
        addStepsToRecipe(recipe, request);
        addTagsToRecipe(recipe, request);

        return toResponse(recipe, userId);
    }

    @Transactional
    public void deleteRecipe(Long recipeId, Long userId) {
        Recipe recipe = findRecipe(recipeId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));

        if (!recipe.getAuthor().getId().equals(userId) && user.getRole() != User.Role.ADMIN) {
            throw BabyRecipeException.forbidden();
        }

        // 레시피 삭제 전 연결된 이미지 파일 제거
        Stream.concat(
            Stream.of(recipe.getImageUrl()),
            recipe.getSteps().stream().map(RecipeStep::getImageUrl)
        ).forEach(imageStorageService::delete);

        recipeRepository.delete(recipe);
    }

    @Transactional
    public boolean toggleLike(Long recipeId, Long userId) {
        Recipe recipe = findRecipe(recipeId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));

        return likeRepository.findByRecipeIdAndUserId(recipeId, userId)
            .map(like -> { likeRepository.delete(like); return false; })
            .orElseGet(() -> { likeRepository.save(Like.builder().recipe(recipe).user(user).build()); return true; });
    }

    @Transactional
    public boolean toggleBookmark(Long recipeId, Long userId) {
        Recipe recipe = findRecipe(recipeId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));

        return bookmarkRepository.findByRecipeIdAndUserId(recipeId, userId)
            .map(bm -> { bookmarkRepository.delete(bm); return false; })
            .orElseGet(() -> { bookmarkRepository.save(Bookmark.builder().recipe(recipe).user(user).build()); return true; });
    }

    private void addIngredientsToRecipe(Recipe recipe, RecipeRequest request) {
        if (request.getIngredients() == null) return;
        for (RecipeRequest.IngredientItem item : request.getIngredients()) {
            Ingredient ingredient = ingredientRepository.findByName(item.getName())
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder().name(item.getName()).build()));
            recipe.addIngredient(RecipeIngredient.builder()
                .recipe(recipe).ingredient(ingredient)
                .amount(item.getAmount()).unit(item.getUnit())
                .build());
        }
    }

    private void addStepsToRecipe(Recipe recipe, RecipeRequest request) {
        if (request.getSteps() == null) return;
        for (RecipeRequest.StepItem item : request.getSteps()) {
            recipe.addStep(RecipeStep.builder()
                .recipe(recipe).stepOrder(item.getOrder())
                .description(item.getDescription()).imageUrl(item.getImageUrl())
                .build());
        }
    }

    private void addTagsToRecipe(Recipe recipe, RecipeRequest request) {
        if (request.getTags() == null) return;
        recipe.getTags().clear();
        for (String tagName : request.getTags()) {
            Tag tag = tagRepository.findByName(tagName)
                .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build()));
            recipe.getTags().add(tag);
        }
    }

    private RecipeResponse toResponse(Recipe recipe, Long currentUserId) {
        long likeCount = recipeRepository.countLikes(recipe.getId());
        long commentCount = recipeRepository.countComments(recipe.getId());
        boolean liked = currentUserId != null && likeRepository.existsByRecipeIdAndUserId(recipe.getId(), currentUserId);
        boolean bookmarked = currentUserId != null && bookmarkRepository.existsByRecipeIdAndUserId(recipe.getId(), currentUserId);
        return RecipeResponse.from(recipe, likeCount, commentCount, liked, bookmarked);
    }

    private Recipe findRecipe(Long id) {
        return recipeRepository.findById(id)
            .orElseThrow(() -> BabyRecipeException.notFound("레시피"));
    }

    private void checkAuthor(Recipe recipe, Long userId) {
        if (!recipe.getAuthor().getId().equals(userId)) {
            throw BabyRecipeException.forbidden();
        }
    }
}
