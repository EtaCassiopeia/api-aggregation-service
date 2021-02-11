package com.fedex.api

import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient.{FedexClient, FedexClientEnv}
import com.fedex.api.http.HttpClientError

package object aggregate {
  type BulkDikeType[I, A] = BulkDike[FedexClient with FedexClientEnv, HttpClientError, I, A]
}
