package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/go-redis/redis/v8"
)

type RedisCache struct {
	client *redis.Client
	ctx    context.Context
}

func NewRedisCache(addr, password string, db int) *RedisCache {
	client := redis.NewClient(&redis.Options{
		Addr:         addr,
		Password:     password,
		DB:           db,
		PoolSize:     10,
		MinIdleConns: 5,
		MaxRetries:   3,
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
		PoolTimeout:  4 * time.Second,
		IdleTimeout:  5 * time.Minute,
	})

	return &RedisCache{
		client: client,
		ctx:    context.Background(),
	}
}

func (r *RedisCache) Set(key string, value interface{}, expiration time.Duration) error {
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	return r.client.Set(r.ctx, key, data, expiration).Err()
}

func (r *RedisCache) Get(key string, dest interface{}) error {
	val, err := r.client.Get(r.ctx, key).Result()
	if err != nil {
		return err
	}
	return json.Unmarshal([]byte(val), dest)
}

func (r *RedisCache) Delete(key string) error {
	return r.client.Del(r.ctx, key).Err()
}

func (r *RedisCache) Exists(key string) bool {
	val, _ := r.client.Exists(r.ctx, key).Result()
	return val > 0
}

func (r *RedisCache) SetWithTags(key string, value interface{}, tags []string, expiration time.Duration) error {
	if err := r.Set(key, value, expiration); err != nil {
		return err
	}

	for _, tag := range tags {
		tagKey := fmt.Sprintf("tag:%s", tag)
		if err := r.client.SAdd(r.ctx, tagKey, key).Err(); err != nil {
			return err
		}
		r.client.Expire(r.ctx, tagKey, expiration)
	}
	return nil
}

func (r *RedisCache) InvalidateTag(tag string) error {
	tagKey := fmt.Sprintf("tag:%s", tag)
	keys, err := r.client.SMembers(r.ctx, tagKey).Result()
	if err != nil {
		return err
	}

	for _, key := range keys {
		r.client.Del(r.ctx, key)
	}
	return r.client.Del(r.ctx, tagKey).Err()
}

func (r *RedisCache) Ping() error {
	return r.client.Ping(r.ctx).Err()
}

func (r *RedisCache) Close() error {
	return r.client.Close()
}