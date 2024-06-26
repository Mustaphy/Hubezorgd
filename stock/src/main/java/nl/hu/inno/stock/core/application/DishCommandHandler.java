package nl.hu.inno.stock.core.application;

import jakarta.transaction.Transactional;
import nl.hu.inno.stock.core.application.command.CreateDish;
import nl.hu.inno.stock.core.application.command.PostDishReview;
import nl.hu.inno.stock.core.application.command.PrepareDishes;
import nl.hu.inno.stock.core.application.query.IsAvailable;
import nl.hu.inno.stock.core.data.DishEventPublisher;
import nl.hu.inno.stock.core.data.storage.DishRepository;
import nl.hu.inno.stock.core.data.storage.DishReviewRepository;
import nl.hu.inno.stock.core.data.storage.IngredientRepository;
import nl.hu.inno.stock.core.domain.Dish;
import nl.hu.inno.stock.core.domain.DishReview;
import nl.hu.inno.stock.core.domain.Ingredient;
import nl.hu.inno.stock.core.domain.ReviewRating;
import nl.hu.inno.stock.core.domain.event.DishEvent;
import nl.hu.inno.stock.core.domain.event.DishesPreparedEvent;
import nl.hu.inno.stock.core.domain.exception.DishNotFoundException;
import nl.hu.inno.stock.core.domain.exception.IngredientNotFoundException;
import nl.hu.inno.stock.core.domain.exception.OutOfStockException;
import nl.hu.inno.stock.infrastructure.dto.DishDTO;
import nl.hu.inno.stock.infrastructure.dto.DishReviewDTO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
public class DishCommandHandler {
    private final DishQueryHandler dishQueryHandler;
    private final DishRepository dishRepository;
    private final DishReviewRepository dishReviewRepository;
    private final IngredientRepository ingredientRepository;
    private final DishEventPublisher eventPublisher;

    public DishCommandHandler(DishQueryHandler dishQueryHandler, DishRepository dishRepository, DishReviewRepository dishReviewRepository, IngredientRepository ingredientRepository, DishEventPublisher eventPublisher) {
        this.dishQueryHandler = dishQueryHandler;
        this.dishRepository = dishRepository;
        this.dishReviewRepository = dishReviewRepository;
        this.ingredientRepository = ingredientRepository;
        this.eventPublisher = eventPublisher;
    }

    public DishDTO handle(CreateDish command) {
        List<Ingredient> ingredients = command
                .ingredientsIds()
                .stream()
                .map(ingredientId -> this.ingredientRepository
                        .findById(ingredientId)
                        .orElseThrow(() -> new IngredientNotFoundException(String.format("Ingredient with id '%s' could not be found.", ingredientId))))
                .toList();

        Dish dish = Dish.create(command.name(), ingredients);

        this.publishEventsAndSave(dish);

        return DishDTO.toDTO(dish);
    }

    public void handle(PrepareDishes command) {
        if (!dishQueryHandler.handle(new IsAvailable(command.orderedDishes()))) {
            throw new OutOfStockException("At least one of the dishes is out of stock.");
        }

        command.orderedDishes().forEach(orderedDish -> {
            Dish dish = this.dishRepository
                    .findById(orderedDish.id())
                    .orElseThrow(() -> new DishNotFoundException(String.format("Dish with id '%s' could not be found.", orderedDish.id())));

            dish.prepare(orderedDish.nr());

            this.ingredientRepository.saveAll(dish.getIngredients());

            this.publishEventsAndSave(dish);
        });

        this.eventPublisher.publish(new DishesPreparedEvent(command.order()));
    }

    public DishReviewDTO handle(PostDishReview command) {
        Dish dish = this.dishRepository
                .findById(command.id())
                .orElseThrow(() -> new DishNotFoundException(String.format("Dish with id '%s' could not be found.", command.id())));

        return DishReviewDTO.toDTO(this.dishReviewRepository.save(new DishReview(dish, ReviewRating.fromInt(command.rating()), command.description(), command.user())));
    }

    private void publishEventsAndSave(Dish dish) {
        List<DishEvent> events = dish.listEvents();
        events.forEach(this.eventPublisher::publish);
        dish.clearEvents();

        this.dishRepository.save(dish);
    }
}

