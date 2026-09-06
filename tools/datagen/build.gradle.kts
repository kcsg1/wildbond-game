// docs/architecture.md §5.1 — datagen 은 :data 의 소스를 생성하므로 :data 에 의존하지 않는다
// (의존하면 :data:compileJava → :tools:datagen → :data 순환이 생긴다). plan.md 단계 1 "결정" 참고.
